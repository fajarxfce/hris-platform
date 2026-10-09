package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.util.UUID

interface PayrollReviewRepository {
    fun find(company: UUID, id: UUID): Result<PayrollReview?>

    fun list(company: UUID, run: UUID, after: Int?, limit: Int): Result<Page<PayrollReview>>

    fun latest(company: UUID, run: UUID): Result<PayrollReview?>

    fun totals(company: UUID, run: UUID): Result<PayrollRunTotals>

    fun create(company: UUID, review: PayrollReview): Result<MutationReceipt>

    fun transition(
        company: UUID,
        review: PayrollReview,
        change: PayrollReviewChange,
    ): Result<MutationReceipt>

    fun changes(company: UUID, review: UUID): Result<List<PayrollReviewChange>>
}
