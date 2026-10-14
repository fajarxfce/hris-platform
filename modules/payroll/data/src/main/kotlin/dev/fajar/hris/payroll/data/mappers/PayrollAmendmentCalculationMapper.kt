package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

fun PayrollAmendmentCalculationsRecord.toAmendmentCalculationSummary(
    json: ObjectMapper
): PayrollAmendmentCalculationSummary {
    val succeeded = status == "SUCCEEDED"
    require(succeeded == (failureCode == null) && (succeeded || status == "FAILED"))
    val difference =
        if (succeeded)
            PayrollAmendmentDifference(
                requireNotNull(differenceTaxableGross),
                requireNotNull(differenceWithheld),
                requireNotNull(differenceTakeHome),
                requireNotNull(differenceEmployeeDeductions),
                requireNotNull(differenceEmployerCost),
            )
        else null
    val failure =
        failureCode?.let { code ->
            Failure(
                FailureKind.valueOf(requireNotNull(failureKind)),
                code,
                fields =
                    json.readValue(
                        failureFields.data(),
                        object : TypeReference<Map<String, String>>() {},
                    ),
                parameters =
                    json.readValue(
                        failureParameters.data(),
                        object : TypeReference<Map<String, String>>() {},
                    ),
            )
        }
    return PayrollAmendmentCalculationSummary(
        id,
        amendmentId,
        calculationNumber,
        draftRevision,
        PayrollAmendmentCalculationStatus.valueOf(status),
        assessmentCount,
        difference,
        failure,
        actorId,
        recordedAt.toInstant(),
        reason,
    )
}

fun PayrollAmendmentResultRow.toAmendmentCalculation(
    json: ObjectMapper
): PayrollAmendmentCalculation {
    require(result.companyId == target.companyId)
    val facts =
        json.readValue(result.facts.data(), PayrollCalculationFactsData::class.java).toDomain()
    val original =
        json.readValue(result.original.data(), PayrollMonthlyCalculationData::class.java).toDomain()
    val calculated =
        json
            .readValue(result.calculation.data(), PayrollMonthlyCalculationData::class.java)
            .toDomain()
    require(facts.month == original.taxInput.month && facts.month == calculated.taxInput.month)
    return PayrollAmendmentCalculation(
        PayrollAmendmentSourceReference(result.assessmentId, result.assessmentRevision),
        target.toTarget(),
        original,
        facts,
        calculated,
        PayrollAmendmentDifference(
            result.differenceTaxableGross,
            result.differenceWithheld,
            result.differenceTakeHome,
            result.differenceEmployeeDeductions,
            result.differenceEmployerCost,
        ),
    )
}

fun PayrollAmendmentCalculationRow.toAmendmentAttempt(
    rows: List<PayrollAmendmentResultRow>,
    json: ObjectMapper,
): PayrollAmendmentCalculationAttempt {
    val summary = this.summary.toAmendmentCalculationSummary(json)
    require(rows.size == summary.assessmentCount && rows.size <= 12)
    require(
        rows.withIndex().all { (index, row) ->
            row.result.companyId == this.summary.companyId &&
                row.result.calculationId == summary.id &&
                row.result.ordinal == index + 1
        }
    )
    if (summary.status == PayrollAmendmentCalculationStatus.FAILED) {
        require(rows.isEmpty())
        return PayrollAmendmentCalculationAttempt(summary, null)
    }
    val versions =
        json.readValue(
            sourceVersions,
            object : TypeReference<List<PayrollAmendmentSourceReferenceData>>() {},
        )
    require(
        versions.size in 1..12 && versions.map { it.assessmentId }.distinct().size == versions.size
    )
    val references = versions.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) }
    val calculations = rows.map { it.toAmendmentCalculation(json) }
    require(
        calculations.isNotEmpty() && calculations.first().source.assessmentId == firstAssessmentId
    )
    require(calculations.all { it.source in references })
    require(
        calculations.zipWithNext().all { (before, after) -> before.facts.month < after.facts.month }
    )
    return PayrollAmendmentCalculationAttempt(
        summary,
        PayrollAmendmentPreview(
            firstAssessmentId,
            java.util.List.copyOf(references),
            java.util.List.copyOf(calculations),
            requireNotNull(summary.difference),
        ),
    )
}

fun PayrollAmendmentCalculationSummary.toRecord(company: UUID, json: ObjectMapper) =
    PayrollAmendmentCalculationsRecord().also {
        it.companyId = company
        it.id = id
        it.amendmentId = amendmentId
        it.calculationNumber = number
        it.draftRevision = draftVersion
        it.status = status.name
        it.assessmentCount = assessmentCount
        it.differenceTaxableGross = difference?.taxableGross
        it.differenceWithheld = difference?.withheld
        it.differenceTakeHome = difference?.takeHome
        it.differenceEmployeeDeductions = difference?.employeeDeductions
        it.differenceEmployerCost = difference?.employerCost
        it.failureKind = failure?.kind?.name
        it.failureCode = failure?.code
        it.failureFields =
            JSONB.valueOf(json.writeValueAsString(failure?.fields ?: emptyMap<String, String>()))
        it.failureParameters =
            JSONB.valueOf(
                json.writeValueAsString(failure?.parameters ?: emptyMap<String, String>())
            )
        it.actorId = actorId
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
    }

fun PayrollAmendmentCalculation.toRecord(
    company: UUID,
    attempt: UUID,
    ordinal: Int,
    json: ObjectMapper,
) =
    PayrollAmendmentResultsRecord().also {
        it.companyId = company
        it.calculationId = attempt
        it.ordinal = ordinal
        it.assessmentId = source.assessmentId
        it.assessmentRevision = source.revision
        it.facts = JSONB.valueOf(json.writeValueAsString(facts.toSnapshotData()))
        it.original = JSONB.valueOf(json.writeValueAsString(original.toSnapshotData()))
        it.calculation = JSONB.valueOf(json.writeValueAsString(calculation.toSnapshotData()))
        it.differenceTaxableGross = difference.taxableGross
        it.differenceWithheld = difference.withheld
        it.differenceTakeHome = difference.takeHome
        it.differenceEmployeeDeductions = difference.employeeDeductions
        it.differenceEmployerCost = difference.employerCost
    }
