package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.PayrollAmendmentReviewDataSource
import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollAmendmentReviewRepository
import java.util.UUID

class StoredPayrollAmendmentReviewRepository(private val source: PayrollAmendmentReviewDataSource) :
    PayrollAmendmentReviewRepository {
    override fun find(company: UUID, id: UUID): Result<PayrollAmendmentReview?> = safeDatabaseCall {
        source.find(company, id)?.let {
            require(it.companyId == company && it.id == id)
            it.toAmendmentReview()
        }
    }

    override fun latest(company: UUID, amendmentId: UUID): Result<PayrollAmendmentReview?> =
        safeDatabaseCall {
            source.latest(company, amendmentId)?.let {
                require(it.companyId == company && it.amendmentId == amendmentId)
                it.toAmendmentReview()
            }
        }

    override fun list(company: UUID, amendmentId: UUID): Result<List<PayrollAmendmentReview>> =
        safeDatabaseCall {
            val rows = source.list(company, amendmentId)
            require(
                rows.size <= 8 &&
                    rows.all { it.companyId == company && it.amendmentId == amendmentId }
            )
            java.util.List.copyOf(rows.map { it.toAmendmentReview() })
        }

    override fun create(company: UUID, review: PayrollAmendmentReview): Result<Unit> =
        safeDatabaseCall {
            source.insert(review.toRecord(company))
            source.append(
                PayrollAmendmentReviewChange(
                        0,
                        review.amendmentVersion + 1,
                        PayrollAmendmentAction.SUBMITTED,
                        PayrollAmendmentReviewStatus.PENDING,
                        0,
                        null,
                        review.submittedBy,
                        null,
                        review.reason,
                        review.submittedAt,
                    )
                    .toRecord(company, review)
            )
        }

    override fun transition(
        company: UUID,
        review: PayrollAmendmentReview,
        change: PayrollAmendmentReviewChange,
    ): Result<Unit> =
        safeDatabaseCall { source.update(company, review.id, review.version, change.status.name) }
            .requireCurrentVersion()
            .flatMap { safeDatabaseCall { source.append(change.toRecord(company, review)) } }

    override fun changes(company: UUID, id: UUID): Result<List<PayrollAmendmentReviewChange>> =
        safeDatabaseCall {
            val rows = source.changes(company, id)
            require(rows.size <= 10 && rows.all { it.companyId == company && it.reviewId == id })
            java.util.List.copyOf(rows.map { it.toAmendmentReviewChange() })
        }
}
