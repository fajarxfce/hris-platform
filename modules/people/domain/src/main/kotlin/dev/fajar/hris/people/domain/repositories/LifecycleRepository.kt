package dev.fajar.hris.people.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import java.time.LocalDate
import java.util.UUID

interface LifecycleRepository {
    fun lockTemplates(companyId: UUID): Result<Unit>

    fun templateCount(companyId: UUID): Result<Int>

    fun template(companyId: UUID, id: UUID): Result<LifecycleTemplate?>

    fun templates(companyId: UUID, after: String?, limit: Int): Result<Page<LifecycleTemplate>>

    fun saveTemplate(
        actor: Actor,
        template: LifecycleTemplate,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt>

    fun lockCase(companyId: UUID, id: UUID): Result<Unit>

    fun case(companyId: UUID, id: UUID): Result<LifecycleCase?>

    fun cases(
        companyId: UUID,
        employeeId: UUID?,
        status: LifecycleStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<LifecycleCase>>

    fun completedOffboardingDate(companyId: UUID, employeeId: UUID): Result<LocalDate?>

    fun hasOpenCases(companyId: UUID, employeeId: UUID, exceptId: UUID? = null): Result<Boolean>

    fun create(actor: Actor, case: LifecycleCase, reason: String): Result<MutationReceipt>

    fun changeTask(
        actor: Actor,
        caseId: UUID,
        expectedVersion: Long,
        task: LifecycleTask,
        event: LifecycleEvent,
    ): Result<MutationReceipt>

    fun finish(
        actor: Actor,
        id: UUID,
        expectedVersion: Long,
        status: LifecycleStatus,
        event: LifecycleEvent,
    ): Result<MutationReceipt>

    fun history(companyId: UUID, id: UUID, after: Long?, limit: Int): Result<Page<LifecycleEvent>>

    fun assigned(
        companyId: UUID,
        accountId: UUID,
        after: String?,
        limit: Int,
    ): Result<Page<AssignedLifecycleTask>>
}
