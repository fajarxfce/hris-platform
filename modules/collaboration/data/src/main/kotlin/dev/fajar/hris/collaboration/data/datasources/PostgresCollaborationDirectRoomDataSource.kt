package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationDirectRoomBindings.COLLABORATION_DIRECT_ROOM_BINDINGS as B
import dev.fajar.hris.schema.tables.CollaborationDirectRoomProvisioning.COLLABORATION_DIRECT_ROOM_PROVISIONING as P
import dev.fajar.hris.schema.tables.records.CollaborationDirectRoomBindingsRecord
import dev.fajar.hris.schema.tables.records.CollaborationDirectRoomProvisioningRecord
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationDirectRoomDataSource(private val sql: DSLContext) :
    CollaborationDirectRoomDataSource {
    override fun lock(companyId: UUID, conversationId: UUID) {
        sql.execute(
            "select pg_advisory_xact_lock(hashtextextended(?,0))",
            "collaboration-direct-room:$companyId:$conversationId",
        )
    }

    // Normal SELECT is intentional: the room guard serializes mutations. FOR SHARE
    // would also require UPDATE RLS and hide retained metadata from the peer.
    override fun provisioning(
        companyId: UUID,
        conversationId: UUID,
    ): CollaborationDirectRoomProvisioningRecord? =
        sql.selectFrom(P)
            .where(P.COMPANY_ID.eq(companyId), P.CONVERSATION_ID.eq(conversationId))
            .fetchOne()

    override fun binding(
        companyId: UUID,
        conversationId: UUID,
    ): CollaborationDirectRoomBindingsRecord? =
        sql.selectFrom(B)
            .where(B.COMPANY_ID.eq(companyId), B.CONVERSATION_ID.eq(conversationId))
            .fetchOne()

    override fun insert(record: CollaborationDirectRoomProvisioningRecord) {
        sql.insertInto(P).set(record).execute()
    }

    override fun replace(
        record: CollaborationDirectRoomProvisioningRecord,
        expectedVersion: Long,
    ): Boolean =
        sql.update(P)
            .set(P.DEVICE_ID, record.deviceId)
            .set(P.JOB_ID, record.jobId)
            .set(P.VERSION, record.version)
            .set(P.UPDATED_AT, record.updatedAt)
            .where(
                P.COMPANY_ID.eq(record.companyId),
                P.CONVERSATION_ID.eq(record.conversationId),
                P.ACCOUNT_ID.eq(record.accountId),
                P.VERSION.eq(expectedVersion),
            )
            .execute() == 1

    override fun publish(record: CollaborationDirectRoomBindingsRecord) {
        sql.insertInto(B).set(record).execute()
    }
}
