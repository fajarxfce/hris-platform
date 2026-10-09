package dev.fajar.hris.administration.data.mappers

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.schema.tables.records.CompanyClientPolicyRevisionsRecord
import java.time.ZoneOffset.UTC
import java.util.UUID

fun CompanyClientPolicyRevisionsRecord.toClientPolicyRevision() =
    CompanyClientPolicyRevision(
        version,
        activateAt.toInstant(),
        ClientPolicy(
            disabledModules.map { CompanyModule.valueOf(requireNotNull(it)) }.toSet(),
            MinimumClientBuilds(minimumAndroidBuild, minimumIosBuild, minimumWebBuild),
            maintenanceStartsAt?.let {
                MaintenanceWindow(it.toInstant(), requireNotNull(maintenanceEndsAt).toInstant())
            },
        ),
        recordedAt.toInstant(),
        actorId,
        reason,
    )

fun CompanyClientPolicyRevision.toRecord(company: UUID) =
    CompanyClientPolicyRevisionsRecord().also {
        it.companyId = company
        it.version = version
        it.activateAt = activateAt.atOffset(UTC)
        it.disabledModules =
            policy.disabledModules.map { module -> module.name }.sorted().toTypedArray()
        it.minimumAndroidBuild = policy.minimumBuilds.android
        it.minimumIosBuild = policy.minimumBuilds.ios
        it.minimumWebBuild = policy.minimumBuilds.web
        it.maintenanceStartsAt = policy.maintenance?.startsAt?.atOffset(UTC)
        it.maintenanceEndsAt = policy.maintenance?.endsAt?.atOffset(UTC)
        it.recordedAt = recordedAt.atOffset(UTC)
        it.actorId = actorId
        it.reason = reason
    }
