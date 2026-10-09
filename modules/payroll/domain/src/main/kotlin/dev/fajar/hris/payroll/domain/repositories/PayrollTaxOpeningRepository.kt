package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.util.UUID

interface PayrollTaxOpeningRepository {
    fun revision(
        company: UUID,
        employee: UUID,
        year: Int,
        revision: Long,
    ): Result<PayrollTaxOpening?>

    fun find(company: UUID, employee: UUID, year: Int): Result<PayrollTaxOpening?>

    fun history(
        company: UUID,
        employee: UUID,
        year: Int,
        after: Long?,
        limit: Int,
    ): Result<Page<PayrollTaxOpening>>

    fun list(
        company: UUID,
        year: Int,
        status: PayrollTaxOpeningStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollTaxOpening>>

    fun authors(company: UUID, id: UUID): Result<Set<UUID>>

    fun save(
        company: UUID,
        opening: PayrollTaxOpening,
        expectedVersion: Long?,
    ): Result<MutationReceipt>
}
