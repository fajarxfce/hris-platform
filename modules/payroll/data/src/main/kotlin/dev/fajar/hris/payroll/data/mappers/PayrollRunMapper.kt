package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.*
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

fun PayrollRunsRecord.toRun() =
    PayrollRun(
        id,
        periodId,
        runNumber,
        YearMonth.from(earningsMonth),
        incomeDueDate,
        plannedPaymentDate,
        timezone,
        workJobId,
        workPeriodVersion,
        policyRevision,
        totalEmployees,
        actorId,
        reviewReference,
        reason,
        createdAt.toInstant(),
        jobId,
        PayrollRunStatus.valueOf(status),
        version,
        processed,
        succeeded,
        failed,
    )

fun PayrollRunTargetsRecord.toTarget() =
    PayrollRunTarget(
        ordinal,
        employmentId,
        employeeNumber,
        employeeName,
        employmentVersion,
        compensationRevision,
        inputId,
        inputRevision,
        taxOpeningId,
        taxOpeningRevision,
    )

fun PayrollRunAttemptsRecord.toAttempt() =
    PayrollRunAttempt(jobId, attempt, baseCompleted, startedAt.toInstant(), reason)

fun PayrollRunItemRow.toItem(json: ObjectMapper): PayrollRunItem =
    PayrollRunItem(
        target.toTarget(),
        jobId,
        completedAt.toInstant(),
        failureCode?.let {
            Failure(
                FailureKind.valueOf(requireNotNull(failureKind)),
                it,
                fields =
                    json.readValue(fields.data(), object : TypeReference<Map<String, String>>() {}),
                parameters =
                    json.readValue(
                        parameters.data(),
                        object : TypeReference<Map<String, String>>() {},
                    ),
            )
        },
        taxableGross,
        withheld,
        takeHome,
    )

fun PayrollRunResultRow.toResult(json: ObjectMapper): PayrollRunResult {
    val r = result
    val item =
        PayrollRunItemRow(
                target,
                r.jobId,
                r.completedAt,
                r.failureKind,
                r.failureCode,
                r.failureFields,
                r.failureParameters,
                r.taxableGross,
                r.withheld,
                r.takeHome,
            )
            .toItem(json)
    return PayrollRunResult(
        item,
        r.facts?.let {
            json.readValue(it.data(), PayrollCalculationFactsData::class.java).toDomain()
        },
        r.calculation?.let {
            json.readValue(it.data(), PayrollMonthlyCalculationData::class.java).toDomain()
        },
    )
}

fun PayrollRun.toRecord(company: UUID) =
    PayrollRunsRecord().also {
        it.companyId = company
        it.id = id
        it.periodId = periodId
        it.runNumber = number
        it.earningsMonth = earningsMonth.atDay(1)
        it.incomeDueDate = incomeDueDate
        it.plannedPaymentDate = plannedPaymentDate
        it.timezone = timezone
        it.workJobId = workJobId
        it.workPeriodVersion = workPeriodVersion
        it.policyRevision = policyRevision
        it.totalEmployees = totalEmployees
        it.actorId = actorId
        it.reviewReference = reviewReference
        it.reason = reason
        it.createdAt = createdAt.atOffset(ZoneOffset.UTC)
        it.jobId = jobId
        it.status = status.name
        it.version = version
        it.processed = processed
        it.succeeded = succeeded
        it.failed = failed
    }

fun PayrollRunTarget.toRecord(company: UUID, run: PayrollRun) =
    PayrollRunTargetsRecord().also {
        it.companyId = company
        it.runId = run.id
        it.periodId = run.periodId
        it.ordinal = ordinal
        it.employmentId = employeeId
        it.employeeNumber = employeeNumber
        it.employeeName = employeeName
        it.employmentVersion = employmentVersion
        it.compensationRevision = compensationRevision
        it.inputId = inputId
        it.inputRevision = inputRevision
        it.taxOpeningId = taxOpeningId
        it.taxOpeningRevision = taxOpeningRevision
    }

fun PayrollRunAttempt.toRecord(company: UUID, run: UUID) =
    PayrollRunAttemptsRecord().also {
        it.companyId = company
        it.runId = run
        it.jobId = jobId
        it.attempt = number
        it.baseCompleted = baseCompleted
        it.startedAt = startedAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
    }

fun PayrollRunResult.toRecord(company: UUID, run: UUID, json: ObjectMapper) =
    PayrollRunResultsRecord().also {
        it.companyId = company
        it.runId = run
        it.ordinal = item.target.ordinal
        it.jobId = item.jobId
        it.status = if (item.failure == null) "SUCCEEDED" else "FAILED"
        it.completedAt = item.completedAt.atOffset(ZoneOffset.UTC)
        it.failureKind = item.failure?.kind?.name
        it.failureCode = item.failure?.code
        it.failureFields =
            JSONB.valueOf(
                json.writeValueAsString(item.failure?.fields ?: emptyMap<String, String>())
            )
        it.failureParameters =
            JSONB.valueOf(
                json.writeValueAsString(item.failure?.parameters ?: emptyMap<String, String>())
            )
        it.facts =
            facts?.let { value -> JSONB.valueOf(json.writeValueAsString(value.toSnapshotData())) }
        it.calculation =
            calculation?.let { value ->
                JSONB.valueOf(json.writeValueAsString(value.toSnapshotData()))
            }
        it.taxableGross = item.taxableGross
        it.withheld = item.withheld
        it.takeHome = item.takeHome
    }
