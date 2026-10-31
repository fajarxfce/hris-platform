package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.policies.*
import dev.fajar.hris.core.domain.*
import java.time.Instant

internal fun nativeCollaborationClientDeadline(
    effective: EffectiveClientPolicy,
    clientVersion: ClientVersion?,
    now: Instant,
): Result<Instant> {
    // Never admit using an observation whose scheduled successor became effective during I/O.
    if (effective.nextActivation?.let { !it.isAfter(now) } == true)
        return Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_admission_changed"))
    val status = clientPolicyStatus(effective, now)
    return validateClientAvailability(
            status,
            setOf(CompanyModule.COLLABORATION),
            clientVersion,
            native = true,
        )
        .map { status.validUntil }
}
