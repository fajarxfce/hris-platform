package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.util.UUID

interface PayrollAmendmentReviewRepository {
    fun find(company: UUID, id: UUID): Result<PayrollAmendmentReview?>

    fun latest(company: UUID, amendmentId: UUID): Result<PayrollAmendmentReview?>

    fun list(company: UUID, amendmentId: UUID): Result<List<PayrollAmendmentReview>>

    fun create(company: UUID, review: PayrollAmendmentReview): Result<Unit>

    fun transition(
        company: UUID,
        review: PayrollAmendmentReview,
        change: PayrollAmendmentReviewChange,
    ): Result<Unit>

    fun changes(company: UUID, id: UUID): Result<List<PayrollAmendmentReviewChange>>
}
