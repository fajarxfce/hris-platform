package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.domain.entities.PayrollPaymentRecovery
import dev.fajar.hris.schema.tables.records.PayrollPaymentRecoveriesRecord
import java.time.ZoneOffset
import java.util.UUID

fun PayrollPaymentRecoveriesRecord.toRecovery() =
    PayrollPaymentRecovery(
        id,
        assessmentId,
        assessmentRevision,
        paymentVersion,
        amount,
        transactionReference,
        occurredAt.toInstant(),
        recordedAt.toInstant(),
        actorId,
        reason,
    )

fun PayrollPaymentRecovery.toRecord(company: UUID) =
    PayrollPaymentRecoveriesRecord().also {
        it.companyId = company
        it.id = id
        it.assessmentId = assessmentId
        it.assessmentRevision = assessmentRevision
        it.paymentVersion = paymentVersion
        it.amount = amount
        it.transactionReference = transactionReference
        it.occurredAt = occurredAt.atOffset(ZoneOffset.UTC)
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
        it.actorId = actorId
        it.reason = reason
    }
