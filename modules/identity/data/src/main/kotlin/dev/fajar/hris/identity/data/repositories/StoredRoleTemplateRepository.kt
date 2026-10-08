package dev.fajar.hris.identity.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.RoleTemplateDataSource
import dev.fajar.hris.identity.data.mappers.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.RoleTemplateRepository
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

class StoredRoleTemplateRepository(
    private val source: RoleTemplateDataSource,
    private val json: ObjectMapper,
) : RoleTemplateRepository {
    override fun lock(companyId: UUID): Result<Unit> = safeDatabaseCall { source.lock(companyId) }

    override fun find(companyId: UUID, id: UUID): Result<CompanyRoleTemplate?> = safeDatabaseCall {
        source.find(companyId, id)?.toRoleTemplate()
    }

    override fun count(companyId: UUID): Result<Int> = safeDatabaseCall { source.count(companyId) }

    override fun list(
        companyId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<CompanyRoleTemplate>> = safeDatabaseCall {
        val rows = source.list(companyId, after, limit + 1)
        Page(
            rows.take(limit).map { it.toRoleTemplate() },
            if (rows.size > limit) rows[limit - 1].id.toString() else null,
        )
    }

    override fun save(
        actor: Actor,
        template: CompanyRoleTemplate,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                if (expectedVersion == null) {
                    source.insert(template.toRoleRow())
                    0L
                } else source.update(template.toRoleRow(), expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.insertRevision(
                        RoleTemplateRevisionsRecord().also {
                            it.companyId = template.companyId
                            it.roleId = template.id
                            it.revision = version
                            it.code = template.code
                            it.name = template.name
                            it.permissions = template.permissions.sorted().toTypedArray()
                            it.active = template.active
                            it.actorId = actor.accountId
                            it.reason = reason
                        }
                    )
                    MutationReceipt(template.id, version)
                }
            }

    override fun recordApplication(
        actor: Actor,
        accountId: UUID,
        membershipVersion: Long,
        grant: MembershipGrant,
    ): Result<Unit> = safeDatabaseCall {
        source.insertApplication(
            MembershipRoleApplicationsRecord().also {
                it.companyId = requireNotNull(actor.companyId)
                it.accountId = accountId
                it.membershipVersion = membershipVersion
                it.snapshot = JSONB.valueOf(grant.toSnapshot(json))
                it.actorId = actor.accountId
            }
        )
    }

    override fun application(
        companyId: UUID,
        accountId: UUID,
        membershipVersion: Long,
    ): Result<MembershipGrant?> = safeDatabaseCall {
        source.application(companyId, accountId, membershipVersion)?.let {
            membershipGrantFromSnapshot(it, json)
        }
    }
}
