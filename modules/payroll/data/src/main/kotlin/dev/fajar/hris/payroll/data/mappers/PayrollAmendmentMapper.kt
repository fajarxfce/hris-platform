package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun PayrollAmendmentTerms.toData() =
    PayrollAmendmentTermsData(
        sourceVersions.map { PayrollAmendmentSourceReferenceData(it.assessmentId, it.revision) },
        corrections.map {
            PayrollAmendmentRemunerationData(
                it.assessmentId,
                it.compensation?.toData(),
                it.input?.toData(),
            )
        },
    )

fun PayrollAmendmentTermsData.toTerms(): PayrollAmendmentTerms {
    require(sourceVersions.size in 1..12 && corrections.size in 1..12)
    require(sourceVersions.map { it.assessmentId }.distinct().size == sourceVersions.size)
    require(sourceVersions.all { it.revision >= 0 && it.revision < Long.MAX_VALUE })
    require(corrections.map { it.assessmentId }.distinct().size == corrections.size)
    require(
        corrections.all { item ->
            (item.compensation != null || item.input != null) &&
                sourceVersions.any { it.assessmentId == item.assessmentId }
        }
    )
    return PayrollAmendmentTerms(
        java.util.List.copyOf(
            sourceVersions.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) }
        ),
        java.util.List.copyOf(
            corrections.map {
                PayrollAmendmentRemuneration(
                    it.assessmentId,
                    it.compensation?.toTerms(),
                    it.input?.toTerms(),
                )
            }
        ),
    )
}

fun PayrollAmendmentSummaryRow.toAmendmentSummary(): PayrollAmendmentSummary {
    require(revision in 0..header.version && header.firstMonth.dayOfMonth == 1)
    return PayrollAmendmentSummary(
        header.id,
        header.firstAssessmentId,
        YearMonth.from(header.firstMonth),
        header.personId,
        PayrollEmployee(
            header.employmentId,
            header.employeeNumber,
            header.employeeName,
            header.employmentVersion,
        ),
        revision,
        PayrollAmendmentStatus.valueOf(status),
        PayrollAmendmentAction.valueOf(action),
        latestCalculationId,
        header.createdAt.toInstant(),
        actorId,
        recordedAt.toInstant(),
        reason,
        latestReviewId,
    )
}

fun PayrollAmendmentRow.toAmendment(json: ObjectMapper): PayrollAmendment {
    val terms = json.readValue(this.terms, PayrollAmendmentTermsData::class.java).toTerms()
    require(terms.corrections.any { it.assessmentId == summary.header.firstAssessmentId })
    return PayrollAmendment(summary.toAmendmentSummary(), terms)
}

fun PayrollAmendmentSummary.toHeaderRecord(company: UUID) =
    PayrollAmendmentsRecord().also {
        it.companyId = company
        it.id = id
        it.firstAssessmentId = firstAssessmentId
        it.firstMonth = firstMonth.atDay(1)
        it.personId = personId
        it.employmentId = employee.id
        it.employeeNumber = employee.employeeNumber
        it.employeeName = employee.name
        it.employmentVersion = employee.employmentVersion
        it.version = version
        it.createdBy = actorId
        it.createdAt = createdAt.atOffset(ZoneOffset.UTC)
    }

fun PayrollAmendment.toRevisionRecord(company: UUID, json: ObjectMapper) =
    PayrollAmendmentRevisionsRecord().also {
        it.companyId = company
        it.amendmentId = summary.id
        it.revision = summary.version
        it.status = summary.status.name
        it.action = summary.action.name
        it.latestCalculationId = summary.latestCalculationId
        it.latestReviewId = summary.latestReviewId
        it.terms = JSONB.valueOf(json.writeValueAsString(terms.toData()))
        it.actorId = summary.actorId
        it.recordedAt = summary.recordedAt.atOffset(ZoneOffset.UTC)
        it.reason = summary.reason
    }
