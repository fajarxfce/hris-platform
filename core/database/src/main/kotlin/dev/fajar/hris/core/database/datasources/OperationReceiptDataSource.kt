package dev.fajar.hris.core.database.datasources

import dev.fajar.hris.schema.tables.records.OperationReceiptsRecord
import java.util.UUID

interface OperationReceiptDataSource {
    fun lock(companyId: UUID, actorId: UUID, operation: String, operationId: UUID)

    fun find(
        companyId: UUID,
        actorId: UUID,
        operation: String,
        operationId: UUID,
    ): OperationReceiptsRecord?

    fun insert(
        companyId: UUID,
        actorId: UUID,
        operation: String,
        operationId: UUID,
        hash: String,
        resourceId: UUID,
        version: Long,
    )
}
