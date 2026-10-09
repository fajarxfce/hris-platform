package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.domain.entities.PayrollFinalization
import dev.fajar.hris.schema.tables.records.PayrollFinalizationsRecord
import java.time.ZoneOffset
import java.util.UUID

fun PayrollFinalizationsRecord.toFinalization() =
    PayrollFinalization(
        id,
        runId,
        reviewId,
        runVersion,
        reviewVersion,
        approvalVersion,
        attempt,
        jobId,
        actorId,
        requestedAt.toInstant(),
        reason,
        companyCode,
        companyName,
        publishedAt?.toInstant(),
    )

fun PayrollFinalization.toRecord(company: UUID) =
    PayrollFinalizationsRecord().also {
        it.companyId = company
        it.id = id
        it.runId = runId
        it.reviewId = reviewId
        it.runVersion = runVersion
        it.reviewVersion = reviewVersion
        it.approvalVersion = approvalVersion
        it.attempt = number
        it.jobId = jobId
        it.actorId = actorId
        it.requestedAt = requestedAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
        it.companyCode = companyCode
        it.companyName = companyName
    }
