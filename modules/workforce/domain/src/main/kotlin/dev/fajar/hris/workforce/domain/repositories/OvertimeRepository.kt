package dev.fajar.hris.workforce.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.*
import java.util.UUID

interface OvertimeRepository {
    fun lock(companyId: UUID, employeeId: UUID, shared: Boolean = false): Result<Unit>

    fun find(companyId: UUID, id: UUID): Result<OvertimeRequest?>

    fun count(companyId: UUID, employeeId: UUID, month: YearMonth): Result<Int>

    fun overlaps(companyId: UUID, employeeId: UUID, interval: OvertimeInterval): Result<Boolean>

    fun unresolved(companyId: UUID, month: YearMonth): Result<Boolean>

    fun approved(companyId: UUID, employeeId: UUID, month: YearMonth): Result<List<OvertimeRequest>>

    fun list(
        companyId: UUID,
        employeeId: UUID?,
        from: LocalDate,
        until: LocalDate,
        status: OvertimeStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<OvertimeRequest>>

    fun history(companyId: UUID, id: UUID, after: Long?, limit: Int): Result<Page<OvertimeChange>>

    fun create(companyId: UUID, request: OvertimeRequest): Result<MutationReceipt>

    fun update(
        actor: Actor,
        request: OvertimeRequest,
        kind: OvertimeChangeKind,
        reason: String,
        at: Instant,
    ): Result<MutationReceipt>
}
