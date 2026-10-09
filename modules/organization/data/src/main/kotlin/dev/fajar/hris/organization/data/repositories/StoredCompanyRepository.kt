package dev.fajar.hris.organization.data.repositories

import dev.fajar.hris.core.database.*
import dev.fajar.hris.core.database.datasources.OperationReceiptDataSource
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.data.datasources.CompanyDataSource
import dev.fajar.hris.organization.data.mappers.toCompany
import dev.fajar.hris.organization.domain.entities.Company
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredCompanyRepository(
    private val source: CompanyDataSource,
    private val receipts: OperationReceiptDataSource,
    private val json: ObjectMapper,
) : CompanyRepository {
    override fun lock(id: UUID, shared: Boolean): Result<Unit> = safeDatabaseCall {
        source.lock(id, shared)
    }

    override fun find(id: UUID): Result<Company?> = safeDatabaseCall {
        source.find(id)?.toCompany()
    }

    override fun create(
        actor: Actor,
        operationId: UUID,
        company: Company,
    ): Result<MutationReceipt> =
        idempotentWrite(
            receipts,
            json,
            actor,
            "company.create",
            operationId,
            listOf(company.code, company.name, company.timezone),
        ) {
            safeDatabaseCall {
                val row = source.insert(company.id, company.code, company.name, company.timezone)
                MutationReceipt(row.id, row.version)
            }
        }

    override fun update(
        actor: Actor,
        operationId: UUID,
        company: Company,
    ): Result<MutationReceipt> =
        idempotentWrite(
            receipts,
            json,
            actor,
            "company.update",
            operationId,
            listOf(company.code, company.name, company.timezone, company.version.toString()),
        ) {
            safeDatabaseCall {
                    source
                        .update(
                            company.id,
                            company.code,
                            company.name,
                            company.timezone,
                            company.version,
                        )
                        ?.let { MutationReceipt(it.id, it.version) }
                }
                .flatMap {
                    if (it == null) Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                    else Result.Success(it)
                }
        }
}
