package dev.fajar.hris.expenses.data.mappers

import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.schema.tables.records.ExpenseReviewsRecord
import java.time.ZoneOffset
import java.util.UUID

fun ExpenseReviewsRecord.toReview() =
    ExpenseReview(
        requireNotNull(submissionId),
        requireNotNull(claimId),
        requireNotNull(claimVersion),
        requireNotNull(approvalId),
        requireNotNull(approvalVersion),
        requireNotNull(step),
        requireNotNull(actorId),
        requireNotNull(decidingFor),
        ExpenseDecision.valueOf(requireNotNull(decision)),
        ExpenseClaimStatus.valueOf(requireNotNull(resultingStatus)),
        requireNotNull(duplicateDigests).toSet(),
        requireNotNull(duplicatesAcknowledged),
        requireNotNull(reason),
        requireNotNull(decidedAt).toInstant(),
    )

fun ExpenseReview.toRecord(company: UUID) =
    ExpenseReviewsRecord().also {
        it.companyId = company
        it.submissionId = submissionId
        it.claimId = claimId
        it.claimVersion = claimVersion
        it.approvalId = approvalId
        it.approvalVersion = approvalVersion
        it.step = step
        it.actorId = actorId
        it.decidingFor = decidingFor
        it.decision = decision.name
        it.resultingStatus = status.name
        it.duplicateDigests = duplicateDigests.sorted().toTypedArray()
        it.duplicatesAcknowledged = duplicatesAcknowledged
        it.reason = reason
        it.decidedAt = decidedAt.atOffset(ZoneOffset.UTC)
    }
