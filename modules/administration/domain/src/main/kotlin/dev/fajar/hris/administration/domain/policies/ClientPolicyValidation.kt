package dev.fajar.hris.administration.domain.policies

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Duration
import java.time.Instant

fun validateClientPolicyCommand(command: SaveCompanyClientPolicyCommand): Result<Unit> {
    val fields = buildMap {
        if (command.expectedVersion != null && command.expectedVersion !in 0..9999)
            put("expectedVersion", "invalid_revision")
        if (
            command.reason.isBlank() ||
                command.reason.length > 1000 ||
                command.reason.any { it.isISOControl() }
        )
            put("reason", "invalid_reason")
        val first = Instant.parse("2024-01-01T00:00:00Z")
        val last = Instant.parse("2101-01-01T00:00:00Z")
        for ((field, at) in
            listOf(
                "activateAt" to command.activateAt,
                "maintenance.startsAt" to command.policy.maintenance?.startsAt,
                "maintenance.endsAt" to command.policy.maintenance?.endsAt,
            )) {
            if (at != null && (at.isBefore(first) || !at.isBefore(last)))
                put(field, "invalid_timestamp")
        }
        for ((field, value) in
            listOf(
                "android" to command.policy.minimumBuilds.android,
                "ios" to command.policy.minimumBuilds.ios,
                "web" to command.policy.minimumBuilds.web,
            )) {
            if (value !in 0..999999999) put("minimumBuilds.$field", "invalid_build_number")
        }
        val window = command.policy.maintenance
        if (
            window != null &&
                (!window.endsAt.isAfter(window.startsAt) ||
                    Duration.between(window.startsAt, window.endsAt) > Duration.ofDays(7))
        )
            put("maintenance", "invalid_maintenance_window")
    }
    return if (fields.isEmpty()) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.VALIDATION, "invalid_client_policy", fields = fields))
}

/** Evaluated only for a new command, after access guards and receipt replay. */
fun validateClientPolicyActivation(activateAt: Instant?, now: Instant): Result<Unit> =
    when {
        activateAt == null -> Result.Success(Unit)
        activateAt.isBefore(now) ->
            Result.Failed(Failure(FailureKind.CONFLICT, "client_policy_activation_expired"))
        activateAt.isAfter(now.plus(Duration.ofDays(365))) ->
            Result.Failed(Failure(FailureKind.VALIDATION, "client_policy_activation_too_late"))
        else -> Result.Success(Unit)
    }
