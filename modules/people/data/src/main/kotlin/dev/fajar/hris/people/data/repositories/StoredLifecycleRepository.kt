package dev.fajar.hris.people.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.data.datasources.LifecycleDataSource
import dev.fajar.hris.people.data.mappers.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.LifecycleRepository
import dev.fajar.hris.schema.tables.records.LifecycleTemplateRevisionsRecord
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredLifecycleRepository(
    private val source: LifecycleDataSource,
    private val json: ObjectMapper,
) : LifecycleRepository {
    override fun lockTemplates(companyId: UUID) = safeDatabaseCall {
        source.lockTemplates(companyId)
    }

    override fun templateCount(companyId: UUID) = safeDatabaseCall {
        source.templateCount(companyId)
    }

    override fun template(companyId: UUID, id: UUID) = safeDatabaseCall {
        source.template(companyId, id)?.toLifecycleTemplate(json)
    }

    override fun templates(companyId: UUID, after: String?, limit: Int) = safeDatabaseCall {
        val rows = source.templates(companyId, after, limit + 1)
        Page(
            rows.take(limit).map { it.toLifecycleTemplate(json) },
            if (rows.size > limit) rows[limit - 1].code else null,
        )
    }

    override fun saveTemplate(
        actor: Actor,
        template: LifecycleTemplate,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val company = requireNotNull(actor.companyId)
        return safeDatabaseCall {
                val row = template.toRow(company, json)
                if (expectedVersion == null) {
                    source.insertTemplate(row)
                    0L
                } else source.updateTemplate(row, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    val row = template.toRow(company, json)
                    source.insertTemplateRevision(
                        LifecycleTemplateRevisionsRecord().also {
                            it.companyId = company
                            it.id = template.id
                            it.revision = version
                            it.name = template.name
                            it.active = template.active
                            it.tasks = row.tasks
                            it.actorId = actor.accountId
                            it.reason = reason
                        }
                    )
                    MutationReceipt(template.id, version)
                }
            }
    }

    override fun lockCase(companyId: UUID, id: UUID) = safeDatabaseCall {
        source.lockCase(companyId, id)
    }

    override fun case(companyId: UUID, id: UUID) = safeDatabaseCall {
        source.case(companyId, id)?.toLifecycleCase(source.tasks(companyId, setOf(id)))
    }

    override fun cases(
        companyId: UUID,
        employeeId: UUID?,
        status: LifecycleStatus?,
        after: UUID?,
        limit: Int,
    ) = safeDatabaseCall {
        val rows = source.cases(companyId, employeeId, status?.name, after, limit + 1)
        val tasks =
            source.tasks(companyId, rows.take(limit).map { it.id }.toSet()).groupBy { it.caseId }
        Page(
            rows.take(limit).map { it.toLifecycleCase(tasks[it.id].orEmpty()) },
            if (rows.size > limit) rows[limit - 1].id.toString() else null,
        )
    }

    override fun completedOffboardingDate(companyId: UUID, employeeId: UUID) = safeDatabaseCall {
        source.completedOffboardingDate(companyId, employeeId)
    }

    override fun hasOpenCases(companyId: UUID, employeeId: UUID, exceptId: UUID?) =
        safeDatabaseCall {
            source.hasOpenCases(companyId, employeeId, exceptId)
        }

    override fun create(actor: Actor, case: LifecycleCase, reason: String) = safeDatabaseCall {
        val company = requireNotNull(actor.companyId)
        source.insertCase(case.toRow(company))
        source.insertTasks(case.tasks.map { it.toRow(company, case.id) })
        source.insertEvent(
            LifecycleEvent(
                    0,
                    null,
                    LifecycleAction.CREATED,
                    null,
                    actor.accountId,
                    reason,
                    case.createdAt,
                )
                .toRow(company, case.id)
        )
        MutationReceipt(case.id, 0)
    }

    override fun changeTask(
        actor: Actor,
        caseId: UUID,
        expectedVersion: Long,
        task: LifecycleTask,
        event: LifecycleEvent,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.advanceCase(requireNotNull(actor.companyId), caseId, expectedVersion, "OPEN")
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    val company = requireNotNull(actor.companyId)
                    check(source.updateTask(task.toRow(company, caseId)) == 1)
                    source.insertEvent(event.toRow(company, caseId))
                    MutationReceipt(caseId, version)
                }
            }

    override fun finish(
        actor: Actor,
        id: UUID,
        expectedVersion: Long,
        status: LifecycleStatus,
        event: LifecycleEvent,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.advanceCase(
                    requireNotNull(actor.companyId),
                    id,
                    expectedVersion,
                    status.name,
                )
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.insertEvent(event.toRow(requireNotNull(actor.companyId), id))
                    MutationReceipt(id, version)
                }
            }

    override fun history(companyId: UUID, id: UUID, after: Long?, limit: Int) = safeDatabaseCall {
        val rows = source.history(companyId, id, after, limit + 1)
        Page(
            rows.take(limit).map { it.toLifecycleEvent() },
            if (rows.size > limit) rows[limit - 1].version.toString() else null,
        )
    }

    override fun assigned(companyId: UUID, accountId: UUID, after: String?, limit: Int) =
        safeDatabaseCall {
            val parts = after?.split(':', limit = 2)
            val rows =
                source.assigned(
                    companyId,
                    accountId,
                    parts?.get(0)?.let(UUID::fromString),
                    parts?.get(1),
                    limit + 1,
                )
            Page(
                rows.take(limit).map {
                    AssignedLifecycleTask(
                        it.case.id,
                        it.case.employmentId,
                        LifecycleKind.valueOf(it.case.kind),
                        it.case.version,
                        it.task.toLifecycleTask(),
                    )
                },
                if (rows.size > limit) "${rows[limit-1].case.id}:${rows[limit-1].task.key}"
                else null,
            )
        }
}
