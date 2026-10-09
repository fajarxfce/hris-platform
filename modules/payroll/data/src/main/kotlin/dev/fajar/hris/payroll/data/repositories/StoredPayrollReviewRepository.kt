package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.PayrollReviewDataSource
import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollReviewRepository
import java.util.UUID

class StoredPayrollReviewRepository(private val source: PayrollReviewDataSource) :
    PayrollReviewRepository {
    override fun find(company: UUID, id: UUID): Result<PayrollReview?> = safeDatabaseCall {
        source.find(company, id)?.toReview()
    }

    override fun list(
        company: UUID,
        run: UUID,
        after: Int?,
        limit: Int,
    ): Result<Page<PayrollReview>> = safeDatabaseCall {
        val rows = source.list(company, run, after, limit + 1)
        Page(
            rows.take(limit).map { it.toReview() },
            if (rows.size > limit) rows[limit - 1].reviewNumber.toString() else null,
        )
    }

    override fun latest(company: UUID, run: UUID): Result<PayrollReview?> = safeDatabaseCall {
        source.latest(company, run)?.toReview()
    }

    override fun totals(company: UUID, run: UUID): Result<PayrollRunTotals> = safeDatabaseCall {
        source.totals(company, run).let {
            PayrollRunTotals(it.employeeCount, it.taxableGross, it.withheld, it.takeHome)
        }
    }

    override fun create(company: UUID, review: PayrollReview): Result<MutationReceipt> =
        safeDatabaseCall {
            source.insert(review.toRecord(company))
            source.append(
                PayrollReviewChange(
                        0,
                        PayrollReviewAction.SUBMITTED,
                        PayrollReviewStatus.PENDING,
                        0,
                        null,
                        review.submittedBy,
                        null,
                        review.reason,
                        review.submittedAt,
                    )
                    .toRecord(company, review.id)
            )
            MutationReceipt(review.id, 0)
        }

    override fun transition(
        company: UUID,
        review: PayrollReview,
        change: PayrollReviewChange,
    ): Result<MutationReceipt> =
        safeDatabaseCall { source.update(company, review.id, review.version, change.status.name) }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.append(change.toRecord(company, review.id))
                    MutationReceipt(review.id, version)
                }
            }

    override fun changes(company: UUID, review: UUID): Result<List<PayrollReviewChange>> =
        safeDatabaseCall {
            source.changes(company, review).map { it.toReviewChange() }
        }
}
