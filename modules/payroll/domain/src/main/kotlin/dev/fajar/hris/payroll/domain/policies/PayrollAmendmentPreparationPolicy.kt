package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.util.UUID

/** Bind proposed remuneration to the complete observed year and retained source ownership. */
fun preparePayrollAmendmentCorrections(
    firstAssessmentId: UUID,
    sources: List<PayrollAmendmentSource>,
    observed: List<PayrollAmendmentSourceReference>,
    remuneration: List<PayrollAmendmentRemuneration>,
    attendance: List<PayrollAmendmentAttendance> = emptyList(),
): Result<List<PayrollAmendmentCorrection>> {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    if (
        observed.size !in 1..12 ||
            remuneration.size !in 1..12 ||
            observed.any { it.revision < 0 || it.revision == Long.MAX_VALUE } ||
            observed.map { it.assessmentId }.distinct().size != observed.size ||
            remuneration.map { it.assessmentId }.distinct().size != remuneration.size ||
            remuneration.none { it.assessmentId == firstAssessmentId } ||
            remuneration.any {
                it.compensation == null && it.input == null && it.workAdjustmentId == null
            }
    )
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "invalid_payroll_amendment_corrections")
        )
    if (sources.isEmpty())
        return Result.Failed(Failure(FailureKind.NOT_FOUND, "payroll_assessment_not_found"))
    if (sources.size > 12)
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_amendment_sources_invalid"))
    val current = sources.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) }
    if (current.size != observed.size || current.toSet() != observed.toSet())
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_amendment_sources_changed"))
    val first = sources.indexOfFirst { it.assessmentId == firstAssessmentId }
    if (first < 0)
        return Result.Failed(Failure(FailureKind.NOT_FOUND, "payroll_assessment_not_found"))
    val affected = sources.drop(first).associateBy { it.assessmentId }
    val work = attendance.associateBy { it.assessmentId }
    val corrections = mutableListOf<PayrollAmendmentCorrection>()
    for (change in remuneration) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val source =
            affected[change.assessmentId]
                ?: return Result.Failed(
                    Failure(FailureKind.VALIDATION, "invalid_payroll_amendment_corrections")
                )
        // Tax registration has independently verified evidence. A remuneration proposal cannot
        // present new registration values as the original opening/compensation verification.
        if (change.compensation != null && change.compensation.tax != source.facts.compensation.tax)
            return Result.Failed(
                Failure(
                    FailureKind.CONFLICT,
                    "payroll_amendment_tax_evidence_changed",
                    parameters = mapOf("assessmentId" to source.assessmentId.toString()),
                )
            )
        val selectedAttendance =
            change.workAdjustmentId?.let { id ->
                work[source.assessmentId]?.takeIf {
                    it.companyId == source.companyId &&
                        it.employeeId == source.target.employeeId &&
                        it.workJobId == source.workJobId &&
                        it.month == source.facts.month &&
                        it.adjustment.id == id
                }
                    ?: return Result.Failed(
                        Failure(
                            FailureKind.CONFLICT,
                            "stale_workforce_version",
                            parameters = mapOf("assessmentId" to source.assessmentId.toString()),
                        )
                    )
            }
        corrections +=
            PayrollAmendmentCorrection(
                source.assessmentId,
                source.facts.copy(
                    compensation = change.compensation ?: source.facts.compensation,
                    input = change.input ?: source.facts.input,
                    workDays = selectedAttendance?.days ?: source.facts.workDays,
                    workAdjustmentId = change.workAdjustmentId ?: source.facts.workAdjustmentId,
                ),
            )
    }
    return Result.Success(java.util.List.copyOf(corrections))
}

/** New preparation/calculation selects current evidence; review/publication keeps it pinned. */
fun requireCurrentPayrollAmendmentAttendance(
    attendance: List<PayrollAmendmentAttendance>
): Result<Unit> {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    for (source in attendance) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (source.adjustment.id != source.latestAdjustmentId)
            return Result.Failed(
                Failure(
                    FailureKind.CONFLICT,
                    "stale_workforce_version",
                    parameters = mapOf("assessmentId" to source.assessmentId.toString()),
                )
            )
    }
    return Result.Success(Unit)
}

fun payrollAmendmentAttendanceReferences(
    corrections: List<PayrollAmendmentRemuneration>
): Map<UUID, UUID> =
    corrections
        .mapNotNull { change -> change.workAdjustmentId?.let { change.assessmentId to it } }
        .toMap()
