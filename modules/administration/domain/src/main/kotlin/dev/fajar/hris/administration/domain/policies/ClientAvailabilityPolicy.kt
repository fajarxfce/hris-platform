package dev.fajar.hris.administration.domain.policies

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Duration
import java.time.Instant

fun clientPolicyStatus(effective: EffectiveClientPolicy, now: Instant): ClientPolicyStatus {
    val policy = effective.revision?.policy ?: ClientPolicy()
    val window = policy.maintenance
    val active = window != null && !now.isBefore(window.startsAt) && now.isBefore(window.endsAt)
    val boundaries = listOfNotNull(effective.nextActivation, window?.startsAt, window?.endsAt)
    val validUntil =
        boundaries
            .filter { it.isAfter(now) }
            .fold(now.plusSeconds(60)) { limit, at -> minOf(limit, at) }
    return ClientPolicyStatus(effective.revision?.version, policy, active, now, validUntil)
}

/** Admission policy, independent of authorization. Already admitted operations may finish. */
fun validateClientAvailability(
    status: ClientPolicyStatus,
    modules: Set<CompanyModule>,
    version: ClientVersion?,
    native: Boolean,
): Result<Unit> {
    if (version != null) {
        val incompatible =
            if (native) version.platform == ClientPlatform.WEB
            else version.platform != ClientPlatform.WEB
        if (version.build !in 0..999999999 || incompatible)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_client_version"))
    }
    if (status.maintenanceActive) {
        val endsAt = requireNotNull(status.policy.maintenance).endsAt
        val remaining = Duration.between(status.evaluatedAt, endsAt)
        val retryAfter = (remaining.seconds + if (remaining.nano == 0) 0 else 1).coerceIn(1, 604800)
        return Result.Failed(
            Failure(
                FailureKind.UNAVAILABLE,
                "company_maintenance",
                parameters =
                    mapOf(
                        "endsAt" to endsAt.toString(),
                        "retryAfterSeconds" to retryAfter.toString(),
                    ),
            )
        )
    }
    val builds = status.policy.minimumBuilds
    if (version == null) {
        if ((native && (builds.android > 0 || builds.ios > 0)) || (!native && builds.web > 0))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "client_version_required"))
    } else {
        val minimum =
            when (version.platform) {
                ClientPlatform.ANDROID -> builds.android
                ClientPlatform.IOS -> builds.ios
                ClientPlatform.WEB -> builds.web
            }
        if (version.build < minimum)
            return Result.Failed(
                Failure(
                    FailureKind.FORBIDDEN,
                    "client_update_required",
                    parameters =
                        mapOf(
                            "platform" to version.platform.name,
                            "minimumBuild" to minimum.toString(),
                        ),
                )
            )
    }
    val disabled = modules.intersect(status.policy.disabledModules).minByOrNull { it.name }
    return if (disabled == null) Result.Success(Unit)
    else
        Result.Failed(
            Failure(
                FailureKind.FORBIDDEN,
                "company_module_disabled",
                parameters = mapOf("module" to disabled.name),
            )
        )
}
