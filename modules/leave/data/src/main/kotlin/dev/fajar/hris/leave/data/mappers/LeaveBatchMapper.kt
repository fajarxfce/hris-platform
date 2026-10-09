package dev.fajar.hris.leave.data.mappers

import dev.fajar.hris.leave.data.models.LeavePolicySnapshotData
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.*
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun LeaveBatchesRecord.toBatch(json: ObjectMapper): LeaveBatch =
    LeaveBatch(
        id,
        LeaveBatchKind.valueOf(kind),
        typeId,
        YearMonth.from(period),
        json.readValue(policySnapshot.data(), LeavePolicySnapshotData::class.java).toSnapshot(),
        policyVersion,
        timezone,
        actorId,
        createdAt.toInstant(),
        reason,
        totalEmployees,
        jobId,
        LeaveBatchStatus.valueOf(status),
        version,
    )

fun LeaveBatch.toRow(company: UUID, json: ObjectMapper): LeaveBatchesRecord =
    LeaveBatchesRecord().also {
        it.companyId = company
        it.id = id
        it.typeId = typeId
        it.kind = kind.name
        it.period = period.atDay(1)
        it.policyRevision = policy.revision
        it.policyVersion = policyVersion
        it.policySnapshot = JSONB.valueOf(json.writeValueAsString(policy.toData()))
        it.timezone = timezone
        it.actorId = actorId
        it.createdAt = createdAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
        it.totalEmployees = totalEmployees
        it.jobId = jobId
        it.status = status.name
        it.version = version
    }

fun LeaveBatchTargetsRecord.toTarget(): LeaveBatchTarget = LeaveBatchTarget(ordinal, employmentId)

fun LeaveBatchTarget.toRow(company: UUID, batch: UUID): LeaveBatchTargetsRecord =
    LeaveBatchTargetsRecord().also {
        it.companyId = company
        it.batchId = batch
        it.ordinal = ordinal
        it.employmentId = employeeId
    }

fun LeaveBatchAttemptsRecord.toAttempt(): LeaveBatchAttempt =
    LeaveBatchAttempt(jobId, attempt, baseCompleted, startedAt.toInstant(), reason)

fun LeaveBatchAttempt.toRow(company: UUID, batch: UUID): LeaveBatchAttemptsRecord =
    LeaveBatchAttemptsRecord().also {
        it.companyId = company
        it.batchId = batch
        it.jobId = jobId
        it.attempt = number
        it.baseCompleted = baseCompleted
        it.startedAt = startedAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
    }

fun decodeLeaveBatchParameters(value: JSONB, json: ObjectMapper): Map<String, String> {
    val node = json.readTree(value.data())
    require(node.isObject && node.size() <= 16)
    return node.properties().associate {
        require(it.value.isString)
        it.key to it.value.asString()
    }
}

fun LeaveBatchResultsRecord.toResult(json: ObjectMapper): LeaveBatchResult =
    LeaveBatchResult(
        ordinal,
        employmentId,
        jobId,
        LeaveBatchResultStatus.valueOf(status),
        resourceId,
        failureCode,
        decodeLeaveBatchParameters(parameters, json),
        completedAt.toInstant(),
    )

fun LeaveBatchResult.toRow(
    company: UUID,
    batch: LeaveBatch,
    json: ObjectMapper,
): LeaveBatchResultsRecord =
    LeaveBatchResultsRecord().also {
        it.companyId = company
        it.batchId = batch.id
        it.ordinal = ordinal
        it.employmentId = employeeId
        it.typeId = batch.typeId
        it.batchKind = batch.kind.name
        it.jobId = jobId
        it.status = status.name
        it.resourceId = resourceId
        it.failureCode = failureCode
        it.parameters = JSONB.valueOf(json.writeValueAsString(parameters))
        it.completedAt = completedAt.atOffset(ZoneOffset.UTC)
    }
