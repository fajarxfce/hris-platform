package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.PayrollAmendmentCandidateResponse
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentCandidate

fun PayrollAmendmentCandidate.toResponse() =
    PayrollAmendmentCandidateResponse(
        assessmentId,
        employeeId,
        employeeNumber,
        employeeName,
        taxMonth.toString(),
        revision,
        publishedAt,
    )
