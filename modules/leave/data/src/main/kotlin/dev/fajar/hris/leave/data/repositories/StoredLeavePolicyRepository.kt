package dev.fajar.hris.leave.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.data.datasources.LeavePolicyDataSource
import dev.fajar.hris.leave.data.mappers.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeavePolicyRepository
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

class StoredLeavePolicyRepository(
    private val source: LeavePolicyDataSource,
    private val json: ObjectMapper,
) : LeavePolicyRepository {
    override fun lock(companyId: UUID): Result<Unit> = safeDatabaseCall { source.lock(companyId) }

    override fun find(companyId: UUID, id: UUID): Result<LeaveType?> = safeDatabaseCall {
        source.find(companyId, id)?.toType(json)
    }

    override fun effective(companyId: UUID, id: UUID, asOf: LocalDate): Result<LeaveType?> =
        safeDatabaseCall {
            source.effective(companyId, id, asOf)?.toType(json)
        }

    override fun list(
        companyId: UUID,
        asOf: LocalDate,
        after: String?,
        limit: Int,
    ): Result<Page<LeaveType>> = safeDatabaseCall {
        val rows = source.list(companyId, asOf, after, limit + 1)
        Page(
            rows.take(limit).map { it.toType(json) },
            if (rows.size > limit) rows[limit - 1].code else null,
        )
    }

    override fun save(
        actor: Actor,
        type: LeaveType,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val company = requireNotNull(actor.companyId)
        return safeDatabaseCall {
                if (expectedVersion == null) {
                    source.insert(
                        LeaveTypesRecord().also {
                            it.companyId = company
                            it.id = type.id
                            it.code = type.code
                            it.version = 0
                        }
                    )
                    0L
                } else source.advanceVersion(company, type.id, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.append(
                        LeaveTypeRevisionsRecord().also {
                            it.companyId = company
                            it.typeId = type.id
                            it.revision = version
                            it.effectiveFrom = type.effectiveFrom
                            it.details =
                                JSONB.valueOf(json.writeValueAsString(type.policy.toData()))
                            it.active = type.active
                            it.actorId = actor.accountId
                            it.reason = reason
                        }
                    )
                    MutationReceipt(type.id, version)
                }
            }
    }
}
