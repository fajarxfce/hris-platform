package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.PayrollPolicyDataSource
import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollPolicyRepository
import dev.fajar.hris.schema.tables.records.*
import java.time.YearMonth
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

class StoredPayrollPolicyRepository(
    private val source: PayrollPolicyDataSource,
    private val json: ObjectMapper,
) : PayrollPolicyRepository {
    override fun lock(companyId: UUID, shared: Boolean): Result<Unit> = safeDatabaseCall {
        source.lock(companyId, shared)
    }

    override fun current(companyId: UUID): Result<PayrollPolicy?> = safeDatabaseCall {
        source.current(companyId)?.toPolicy(json)
    }

    override fun effective(companyId: UUID, month: YearMonth): Result<PayrollPolicy?> =
        safeDatabaseCall {
            source.effective(companyId, month.atDay(1))?.toPolicy(json)
        }

    override fun history(
        companyId: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<PayrollPolicyRevision>> = safeDatabaseCall {
        val rows = source.history(companyId, after, limit + 1)
        Page(
            rows.take(limit).map { it.toHistory(json) },
            if (rows.size > limit) rows[limit - 1].revision.revision.toString() else null,
        )
    }

    override fun save(
        actor: Actor,
        policy: PayrollPolicy,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                val company = requireNotNull(actor.companyId)
                if (expectedVersion == null) {
                    source.insert(
                        PayrollPoliciesRecord().also {
                            it.companyId = company
                            it.version = 0
                        }
                    )
                    0L
                } else source.advance(company, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.append(
                        PayrollPolicyRevisionsRecord().also {
                            it.companyId = requireNotNull(actor.companyId)
                            it.revision = version
                            it.effectiveFrom = policy.effectiveFrom.atDay(1)
                            it.effectiveUntil = policy.effectiveUntil.atDay(1)
                            it.details = JSONB.valueOf(json.writeValueAsString(policy.toData()))
                            it.actorId = actor.accountId
                            it.reason = reason
                        }
                    )
                    MutationReceipt(requireNotNull(actor.companyId), version)
                }
            }
}
