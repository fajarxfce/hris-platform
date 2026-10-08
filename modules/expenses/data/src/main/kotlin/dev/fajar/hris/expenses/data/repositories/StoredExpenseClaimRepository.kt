package dev.fajar.hris.expenses.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.data.datasources.ExpenseClaimDataSource
import dev.fajar.hris.expenses.data.datasources.ExpenseSubmissionDataSource
import dev.fajar.hris.expenses.data.mappers.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.repositories.ExpenseClaimRepository
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class StoredExpenseClaimRepository(
    private val source: ExpenseClaimDataSource,
    private val submissions: ExpenseSubmissionDataSource,
) : ExpenseClaimRepository {
    override fun reviews(companyId: UUID, submissionId: UUID): Result<List<ExpenseReview>> =
        safeDatabaseCall {
            submissions.reviews(companyId, submissionId).map { it.toReview() }
        }

    override fun review(
        actor: Actor,
        claim: ExpenseClaim,
        review: ExpenseReview,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.review(
                    requireNotNull(actor.companyId),
                    claim.id,
                    claim.version,
                    review.status.name,
                )
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    val company = requireNotNull(actor.companyId)
                    submissions.appendReview(review.toRecord(company))
                    source.appendChange(
                        ExpenseClaimChange(
                                claim.id,
                                version,
                                claim.draftRevision,
                                review.status,
                                ExpenseClaimChangeKind.REVIEWED,
                                actor.accountId,
                                review.reason,
                                review.decidedAt,
                                review.submissionId,
                            )
                            .toRecord(company)
                    )
                    MutationReceipt(claim.id, version)
                }
            }

    override fun contributors(companyId: UUID, claimId: UUID): Result<Set<UUID>> =
        safeDatabaseCall {
            submissions.contributors(companyId, claimId)
        }

    override fun submission(companyId: UUID, id: UUID): Result<ExpenseSubmission?> =
        safeDatabaseCall {
            submissions.find(companyId, id)?.let { row ->
                val draft =
                    requireNotNull(
                        source.draft(
                            companyId,
                            requireNotNull(row.claimId),
                            requireNotNull(row.draftRevision),
                        )
                    )
                row.toSubmission(
                    draft,
                    submissions.lines(companyId, id),
                    submissions.receipts(companyId, id),
                )
            }
        }

    override fun submissions(
        companyId: UUID,
        claimId: UUID,
        after: Int?,
        limit: Int,
    ): Result<Page<ExpenseSubmissionSummary>> = safeDatabaseCall {
        val rows = submissions.list(companyId, claimId, after, limit + 1)
        Page(
            rows.take(limit).map { it.toSubmissionSummary() },
            if (rows.size > limit) rows[limit - 1].number.toString() else null,
        )
    }

    override fun duplicateReceiptDigests(
        companyId: UUID,
        exceptClaimId: UUID,
        digests: Set<String>,
    ): Result<Set<String>> = safeDatabaseCall {
        submissions.duplicateDigests(companyId, exceptClaimId, digests)
    }

    override fun submit(
        actor: Actor,
        claim: ExpenseClaim,
        submission: ExpenseSubmission,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.submit(
                    requireNotNull(actor.companyId),
                    claim.id,
                    claim.version,
                    submission.id,
                    submission.number,
                )
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    val company = requireNotNull(actor.companyId)
                    submissions.insert(submission.toRecord(company))
                    submissions.insertLines(submission.submittedLineRecords(company))
                    submissions.insertReceipts(submission.submittedReceiptRecords(company))
                    source.appendChange(
                        ExpenseClaimChange(
                                claim.id,
                                version,
                                claim.draftRevision,
                                ExpenseClaimStatus.PENDING,
                                ExpenseClaimChangeKind.SUBMITTED,
                                actor.accountId,
                                submission.reason,
                                submission.submittedAt,
                                submission.id,
                            )
                            .toRecord(company)
                    )
                    MutationReceipt(claim.id, version)
                }
            }

    override fun withdraw(
        actor: Actor,
        claim: ExpenseClaim,
        reason: String,
        at: Instant,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.withdraw(requireNotNull(actor.companyId), claim.id, claim.version)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.appendChange(
                        ExpenseClaimChange(
                                claim.id,
                                version,
                                claim.draftRevision,
                                ExpenseClaimStatus.DRAFT,
                                ExpenseClaimChangeKind.WITHDRAWN,
                                actor.accountId,
                                reason,
                                at,
                                claim.latestSubmissionId,
                            )
                            .toRecord(requireNotNull(actor.companyId))
                    )
                    MutationReceipt(claim.id, version)
                }
            }

    override fun lock(companyId: UUID): Result<Unit> = safeDatabaseCall { source.lock(companyId) }

    override fun capacity(companyId: UUID, accountId: UUID): Result<ExpenseClaimCapacity> =
        safeDatabaseCall {
            source.capacity(companyId, accountId).let {
                ExpenseClaimCapacity(it.companyOpenClaims, it.actorOpenClaims)
            }
        }

    override fun find(companyId: UUID, id: UUID): Result<ExpenseClaim?> = safeDatabaseCall {
        source.find(companyId, id)?.toClaim()
    }

    override fun draft(companyId: UUID, id: UUID, revision: Int): Result<ExpenseDraft?> =
        safeDatabaseCall {
            source.draft(companyId, id, revision)?.let {
                it.toDraft(
                    source.lines(companyId, id, setOf(revision)),
                    source.receipts(companyId, id, setOf(revision)),
                )
            }
        }

    override fun list(
        companyId: UUID,
        employmentId: UUID?,
        status: ExpenseClaimStatus?,
        from: Instant,
        until: Instant,
        after: UUID?,
        limit: Int,
    ): Result<Page<ExpenseClaimSummary>> = safeDatabaseCall {
        val rows =
            source.list(
                companyId,
                employmentId,
                status?.name,
                from.atOffset(ZoneOffset.UTC),
                until.atOffset(ZoneOffset.UTC),
                after,
                limit + 1,
            )
        Page(
            rows.take(limit).map { it.toSummary() },
            if (rows.size > limit) rows[limit - 1].id.toString() else null,
        )
    }

    override fun drafts(
        companyId: UUID,
        id: UUID,
        after: Int?,
        limit: Int,
    ): Result<Page<ExpenseDraft>> = safeDatabaseCall {
        val rows = source.drafts(companyId, id, after, limit + 1)
        val page = rows.take(limit)
        val revisions = page.map { requireNotNull(it.revision) }.toSet()
        val lines = source.lines(companyId, id, revisions).groupBy { it.draftRevision }
        val receipts = source.receipts(companyId, id, revisions).groupBy { it.draftRevision }
        Page(
            page.map { it.toDraft(lines[it.revision].orEmpty(), receipts[it.revision].orEmpty()) },
            if (rows.size > limit) rows[limit - 1].revision.toString() else null,
        )
    }

    override fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<ExpenseClaimChange>> = safeDatabaseCall {
        val rows = source.history(companyId, id, after, limit + 1)
        Page(
            rows.take(limit).map { it.toChange() },
            if (rows.size > limit) rows[limit - 1].version.toString() else null,
        )
    }

    override fun saveDraft(
        actor: Actor,
        claim: ExpenseClaim,
        draft: ExpenseDraft,
        expectedVersion: Long?,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                val company = requireNotNull(actor.companyId)
                if (expectedVersion == null) {
                    source.insert(claim.toRecord(company))
                    0L
                } else source.advance(company, claim.id, expectedVersion, claim.draftRevision)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    val company = requireNotNull(actor.companyId)
                    source.append(draft.toRecord(company))
                    source.insertLines(draft.lineRecords(company))
                    source.insertReceipts(draft.receiptRecords(company))
                    source.appendChange(
                        ExpenseClaimChange(
                                claim.id,
                                version,
                                claim.draftRevision,
                                ExpenseClaimStatus.DRAFT,
                                ExpenseClaimChangeKind.DRAFT_SAVED,
                                actor.accountId,
                                draft.reason,
                                draft.recordedAt,
                                claim.latestSubmissionId,
                            )
                            .toRecord(company)
                    )
                    MutationReceipt(claim.id, version)
                }
            }

    override fun cancel(
        actor: Actor,
        claim: ExpenseClaim,
        reason: String,
        at: Instant,
    ): Result<MutationReceipt> =
        safeDatabaseCall { source.cancel(requireNotNull(actor.companyId), claim.id, claim.version) }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.appendChange(
                        ExpenseClaimChange(
                                claim.id,
                                version,
                                claim.draftRevision,
                                ExpenseClaimStatus.CANCELLED,
                                ExpenseClaimChangeKind.CANCELLED,
                                actor.accountId,
                                reason,
                                at,
                                claim.latestSubmissionId,
                            )
                            .toRecord(requireNotNull(actor.companyId))
                    )
                    MutationReceipt(claim.id, version)
                }
            }
}
