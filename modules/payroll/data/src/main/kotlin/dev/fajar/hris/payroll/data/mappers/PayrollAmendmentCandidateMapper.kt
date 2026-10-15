package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.PayrollAmendmentCandidateRow
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentCandidate
import java.time.YearMonth

fun PayrollAmendmentCandidateRow.toAmendmentCandidate(): PayrollAmendmentCandidate {
    require(revision >= 0 && taxMonth.dayOfMonth == 1)
    require(employeeNumber.isNotBlank() && employeeNumber.length <= 32)
    require(employeeName.isNotBlank() && employeeName.length <= 200)
    return PayrollAmendmentCandidate(
        assessmentId,
        employeeId,
        employeeNumber,
        employeeName,
        YearMonth.from(taxMonth),
        revision,
        publishedAt.toInstant(),
    )
}
