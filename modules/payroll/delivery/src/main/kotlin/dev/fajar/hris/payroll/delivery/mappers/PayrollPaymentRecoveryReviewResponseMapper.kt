package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.PayrollPaymentRecoveryReviewResponse
import dev.fajar.hris.payroll.domain.entities.PayrollPaymentRecoveryReview

fun PayrollPaymentRecoveryReview.toResponse() =
    PayrollPaymentRecoveryReviewResponse(
        assessment.assessmentId,
        assessment.employmentId,
        assessment.employeeNumber,
        assessment.employeeName,
        assessment.taxMonth.toString(),
        assessment.publishedAt,
        payments.toResponse(),
        recoveries.map { it.toResponse() },
        earliestConfirmationAt,
        canConfirm,
        blockers.map { it.name }.toSet(),
    )
