package dev.fajar.hris.administration.delivery.mappers

import dev.fajar.hris.administration.delivery.responses.*
import dev.fajar.hris.administration.domain.entities.*

fun MinimumClientBuilds.toResponse() = MinimumClientBuildsResponse(android, ios, web)

fun MaintenanceWindow.toResponse() = MaintenanceWindowResponse(startsAt, endsAt)

fun CompanyClientPolicySettings.toResponse() =
    CompanyClientPolicySettingsResponse(latest?.toResponse(), effective.toResponse())

fun CompanyClientPolicyRevision.toResponse() =
    CompanyClientPolicyRevisionResponse(
        version,
        activateAt,
        policy.disabledModules.sortedBy { it.name },
        policy.minimumBuilds.toResponse(),
        policy.maintenance?.toResponse(),
        recordedAt,
        actorId,
        reason,
    )

fun ClientPolicyStatus.toResponse() =
    ClientPolicyResponse(
        1,
        version,
        CompanyModule.entries.filter { it !in policy.disabledModules },
        policy.minimumBuilds.toResponse(),
        policy.maintenance?.toResponse(),
        maintenanceActive,
        evaluatedAt,
        validUntil,
    )
