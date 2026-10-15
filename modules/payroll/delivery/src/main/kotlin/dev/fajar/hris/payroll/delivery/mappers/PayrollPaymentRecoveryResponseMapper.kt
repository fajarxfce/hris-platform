package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.PayrollPaymentRecoveryResponse
import dev.fajar.hris.payroll.domain.entities.PayrollPaymentRecovery

fun PayrollPaymentRecovery.toResponse() =
    PayrollPaymentRecoveryResponse(
        id,
        assessmentId,
        assessmentRevision,
        paymentVersion,
        amount.toPlainString(),
        "IDR",
        transactionReference,
        occurredAt,
        recordedAt,
        actorId,
        reason,
    )
