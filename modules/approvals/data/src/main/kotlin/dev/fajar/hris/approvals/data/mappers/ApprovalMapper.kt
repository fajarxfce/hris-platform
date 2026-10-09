package dev.fajar.hris.approvals.data.mappers

import dev.fajar.hris.approvals.data.datasources.TemplateRow
import dev.fajar.hris.approvals.data.models.*
import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun TemplateRow.toTemplate(json: ObjectMapper): ApprovalTemplate =
    ApprovalTemplate(
        template.id,
        template.name,
        ApprovalKind.valueOf(template.kind),
        template.active,
        template.version,
        revision.revision,
        revision.effectiveFrom,
        revision.category,
        revision.minimumAmount,
        json.readValue(revision.stages.data(), Array<StageRuleData>::class.java).map {
            StageRule(AssignmentKind.valueOf(it.assignment), it.accountIds, it.permission)
        },
    )

fun ApprovalRequestsRecord.toRequest(
    json: ObjectMapper,
    overrides: List<ApprovalAssignmentOverridesRecord>,
): ApprovalRequest {
    val effectiveStages =
        json.readValue(stages.data(), Array<ApprovalStageData>::class.java).mapIndexed {
            index,
            stage ->
            ApprovalStage(
                overrides.firstOrNull { it.step == index }?.assignees?.toSet() ?: stage.assignees
            )
        }
    return ApprovalRequest(
        id,
        ApprovalKind.valueOf(kind),
        resourceId,
        authorId,
        requesterId,
        templateId,
        templateRevision,
        effectiveStages,
        currentStep,
        ApprovalStatus.valueOf(status),
        version,
        submittedAt.toInstant(),
        excludedAccountIds.toSet(),
    )
}

fun ApprovalRequest.toRow(companyId: UUID, json: ObjectMapper): ApprovalRequestsRecord =
    ApprovalRequestsRecord().also {
        it.companyId = companyId
        it.id = id
        it.kind = kind.name
        it.resourceId = resourceId
        it.authorId = authorId
        it.requesterId = requesterId
        it.templateId = templateId
        it.templateRevision = templateRevision
        it.stages =
            JSONB.valueOf(
                json.writeValueAsString(stages.map { stage -> ApprovalStageData(stage.assignees) })
            )
        it.currentStep = currentStep
        it.status = status.name
        it.version = version
        it.submittedAt = submittedAt.atOffset(ZoneOffset.UTC)
        it.excludedAccountIds = excludedAccountIds.sorted().toTypedArray()
    }

fun ApprovalDelegationsRecord.toDelegation(): Delegation =
    Delegation(
        id,
        ApprovalKind.valueOf(kind),
        fromAccount,
        toAccount,
        validFrom.toInstant(),
        validUntil.toInstant(),
        active,
        version,
    )

fun Delegation.toRow(companyId: UUID): ApprovalDelegationsRecord =
    ApprovalDelegationsRecord().also {
        it.companyId = companyId
        it.id = id
        it.kind = kind.name
        it.fromAccount = fromAccount
        it.toAccount = toAccount
        it.validFrom = validFrom.atOffset(ZoneOffset.UTC)
        it.validUntil = validUntil.atOffset(ZoneOffset.UTC)
        it.active = active
        it.version = version
    }
