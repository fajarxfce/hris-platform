package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface LifecycleDataSource {
    fun lockTemplates(companyId: UUID, shared: Boolean)

    fun templateCount(companyId: UUID): Int

    fun template(companyId: UUID, id: UUID): LifecycleTemplatesRecord?

    fun templates(companyId: UUID, after: String?, limit: Int): List<LifecycleTemplatesRecord>

    fun insertTemplate(record: LifecycleTemplatesRecord)

    fun updateTemplate(record: LifecycleTemplatesRecord, expectedVersion: Long): Long?

    fun insertTemplateRevision(record: LifecycleTemplateRevisionsRecord)

    fun lockCase(companyId: UUID, id: UUID, shared: Boolean)

    fun case(companyId: UUID, id: UUID): LifecycleCasesRecord?

    fun caseDetails(companyId: UUID, id: UUID): LifecycleCaseRow?

    fun cases(
        companyId: UUID,
        employeeId: UUID?,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<LifecycleCaseRow>

    fun completedOffboardingDate(companyId: UUID, employeeId: UUID): LocalDate?

    fun hasOpenCases(companyId: UUID, employeeId: UUID, exceptId: UUID?): Boolean

    fun tasks(companyId: UUID, ids: Set<UUID>): List<LifecycleTasksRecord>

    fun insertCase(record: LifecycleCasesRecord)

    fun insertTasks(records: List<LifecycleTasksRecord>)

    fun advanceCase(companyId: UUID, id: UUID, expectedVersion: Long, status: String): Long?

    fun updateTask(record: LifecycleTasksRecord): Int

    fun insertEvent(record: LifecycleEventsRecord)

    fun history(companyId: UUID, id: UUID, after: Long?, limit: Int): List<LifecycleEventsRecord>

    fun assigned(
        companyId: UUID,
        accountId: UUID,
        afterCase: UUID?,
        afterKey: String?,
        limit: Int,
    ): List<AssignedLifecycleRow>
}
