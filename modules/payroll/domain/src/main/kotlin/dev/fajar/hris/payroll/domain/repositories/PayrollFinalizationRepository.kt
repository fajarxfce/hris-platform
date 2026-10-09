package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.Instant
import java.util.UUID

interface PayrollFinalizationRepository {
    fun find(company: UUID, id: UUID): Result<PayrollFinalization?>

    fun forJob(company: UUID, job: UUID): Result<PayrollFinalization?>

    fun latest(company: UUID, run: UUID): Result<PayrollFinalization?>

    fun list(company: UUID, run: UUID, after: Int?, limit: Int): Result<Page<PayrollFinalization>>

    fun readiness(company: UUID, run: UUID): Result<PayrollPublicationReadiness>

    fun create(company: UUID, finalization: PayrollFinalization): Result<Unit>

    fun publish(company: UUID, finalization: PayrollFinalization, at: Instant): Result<Unit>
}
