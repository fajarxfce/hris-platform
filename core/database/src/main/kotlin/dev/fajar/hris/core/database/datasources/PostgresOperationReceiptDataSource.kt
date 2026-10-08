package dev.fajar.hris.core.database.datasources

import dev.fajar.hris.schema.tables.OperationReceipts.OPERATION_RECEIPTS
import dev.fajar.hris.schema.tables.records.OperationReceiptsRecord
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.JSONB

class PostgresOperationReceiptDataSource(private val sql: DSLContext) : OperationReceiptDataSource {
    override fun lock(companyId: UUID?, actorId: UUID, operation: String, operationId: UUID) {
        sql.query(
                "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                "$companyId:$actorId:$operation:$operationId",
            )
            .execute()
    }

    override fun find(
        companyId: UUID?,
        actorId: UUID,
        operation: String,
        operationId: UUID,
    ): OperationReceiptsRecord? =
        sql.selectFrom(OPERATION_RECEIPTS)
            .where(OPERATION_RECEIPTS.COMPANY_ID.isNotDistinctFrom(companyId))
            .and(OPERATION_RECEIPTS.ACTOR_ID.eq(actorId))
            .and(OPERATION_RECEIPTS.OPERATION.eq(operation))
            .and(OPERATION_RECEIPTS.OPERATION_ID.eq(operationId))
            .fetchOne()

    override fun insert(
        companyId: UUID?,
        actorId: UUID,
        operation: String,
        operationId: UUID,
        hash: String,
        resourceId: UUID,
        version: Long,
    ) {
        sql.insertInto(OPERATION_RECEIPTS)
            .set(OPERATION_RECEIPTS.COMPANY_ID, companyId)
            .set(OPERATION_RECEIPTS.ACTOR_ID, actorId)
            .set(OPERATION_RECEIPTS.OPERATION, operation)
            .set(OPERATION_RECEIPTS.OPERATION_ID, operationId)
            .set(OPERATION_RECEIPTS.PAYLOAD_HASH, hash)
            .set(OPERATION_RECEIPTS.RESOURCE_ID, resourceId)
            .set(OPERATION_RECEIPTS.RESPONSE, JSONB.valueOf("{\"version\":$version}"))
            .execute()
    }
}
