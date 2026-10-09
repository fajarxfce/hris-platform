package dev.fajar.hris.people.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.data.datasources.EmployeeImportDataSource
import dev.fajar.hris.people.data.mappers.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.EmployeeImportRepository
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

class StoredEmployeeImportRepository(
    private val source: EmployeeImportDataSource,
    private val json: ObjectMapper,
) : EmployeeImportRepository {
    override fun lock(companyId: UUID, id: UUID, shared: Boolean) = safeDatabaseCall {
        source.lock(companyId, id, shared)
    }

    override fun create(actor: Actor, batch: EmployeeImport, rows: List<EmployeeImportRow>) =
        safeDatabaseCall {
            val company = requireNotNull(actor.companyId)
            source.insertBatch(batch.toRow(company))
            source.insertRows(rows.map { it.toRow(company, batch.id, json) })
            source.insertAttempt(
                EmployeeImportAttempt(
                        batch.jobId,
                        EmployeeImportPhase.PREVIEW,
                        actor.accountId,
                        batch.createdAt,
                    )
                    .toRow(company, batch.id)
            )
            MutationReceipt(batch.id, batch.version)
        }

    override fun find(companyId: UUID, id: UUID, lock: Boolean) = safeDatabaseCall {
        source.find(companyId, id, lock)?.toEmployeeImport()
    }

    override fun forJob(companyId: UUID, jobId: UUID, lock: Boolean) = safeDatabaseCall {
        source.forJob(companyId, jobId, lock)?.toEmployeeImport()
    }

    override fun list(companyId: UUID, after: UUID?, limit: Int) = safeDatabaseCall {
        val rows = source.list(companyId, after, limit + 1)
        Page(
            rows.take(limit).map { it.toEmployeeImport() },
            if (rows.size > limit) rows[limit - 1].id.toString() else null,
        )
    }

    override fun rows(companyId: UUID, id: UUID, after: Int?, limit: Int) = safeDatabaseCall {
        val rows = source.rows(companyId, id, after, limit + 1)
        Page(
            rows.take(limit).map { it.toEmployeeImportRow(json) },
            if (rows.size > limit) rows[limit - 1].rowNumber.toString() else null,
        )
    }

    override fun nextRow(companyId: UUID, id: UUID, status: EmployeeImportRowStatus, after: Int) =
        safeDatabaseCall {
            source.nextRow(companyId, id, status.name, after)?.toEmployeeImportRow(json)
        }

    override fun counts(companyId: UUID, id: UUID) = safeDatabaseCall {
        source.counts(companyId, id).mapKeys { EmployeeImportRowStatus.valueOf(it.key) }
    }

    override fun recordOutcome(
        companyId: UUID,
        id: UUID,
        rowNumber: Int,
        expectedStatus: EmployeeImportRowStatus,
        status: EmployeeImportRowStatus,
        issues: Map<String, String>,
        employeeId: UUID?,
    ): Result<Unit> =
        safeDatabaseCall {
                source.outcome(
                    companyId,
                    id,
                    rowNumber,
                    expectedStatus.name,
                    status.name,
                    JSONB.valueOf(json.writeValueAsString(issues)),
                    employeeId,
                )
            }
            .flatMap {
                if (it == 1) Result.Success(Unit)
                else Result.Failed(Failure(FailureKind.CONFLICT, "employee_import_row_changed"))
            }

    override fun transition(
        actor: Actor,
        batch: EmployeeImport,
        status: EmployeeImportStatus,
        attempt: EmployeeImportAttempt?,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.transition(
                    requireNotNull(actor.companyId),
                    batch.id,
                    batch.version,
                    status.name,
                    attempt?.jobId ?: batch.jobId,
                )
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    if (attempt != null)
                        source.insertAttempt(
                            attempt.toRow(requireNotNull(actor.companyId), batch.id)
                        )
                    MutationReceipt(batch.id, version)
                }
            }

    override fun attempts(companyId: UUID, id: UUID, after: UUID?, limit: Int) = safeDatabaseCall {
        val rows = source.attempts(companyId, id, after, limit + 1)
        Page(
            rows.take(limit).map { it.toImportAttempt() },
            if (rows.size > limit) rows[limit - 1].jobId.toString() else null,
        )
    }
}
