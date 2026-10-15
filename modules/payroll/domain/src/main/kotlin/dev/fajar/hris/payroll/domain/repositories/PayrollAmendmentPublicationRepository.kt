package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentPublication
import java.time.Instant
import java.util.UUID

interface PayrollAmendmentPublicationRepository {
    fun find(company: UUID, amendmentId: UUID, id: UUID): Result<PayrollAmendmentPublication?>

    fun forJob(company: UUID, jobId: UUID): Result<PayrollAmendmentPublication?>

    /** An amendment admits no more than eight retained attempts. */
    fun list(company: UUID, amendmentId: UUID): Result<List<PayrollAmendmentPublication>>

    fun create(company: UUID, publication: PayrollAmendmentPublication): Result<Unit>

    /** Promote the complete approved suffix; the use case owns guards and the transaction. */
    fun publish(company: UUID, publication: PayrollAmendmentPublication, at: Instant): Result<Unit>

    fun stop(
        company: UUID,
        publication: PayrollAmendmentPublication,
        at: Instant,
        code: String,
        actorId: UUID,
    ): Result<Unit>
}
