package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationReadinessControlTargets.COLLABORATION_READINESS_CONTROL_TARGETS as T
import dev.fajar.hris.schema.tables.CollaborationReadinessControls.COLLABORATION_READINESS_CONTROLS as W
import dev.fajar.hris.schema.tables.records.CollaborationReadinessControlTargetsRecord
import dev.fajar.hris.schema.tables.records.CollaborationReadinessControlsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationReadinessControlQueueDataSource(private val sql: DSLContext) :
    CollaborationReadinessControlQueueDataSource {
    override fun claim(ownerId: UUID, limit: Int): List<CollaborationReadinessControlsRecord> =
        sql.selectFrom(
                DSL.table(
                    "claim_collaboration_readiness_controls({0},{1})",
                    DSL.`val`(ownerId),
                    DSL.`val`(limit),
                )
            )
            .fetchInto(CollaborationReadinessControlsRecord::class.java)

    override fun find(
        lease: CollaborationReadinessLeaseData,
        lock: Boolean,
    ): CollaborationReadinessControlsRecord? {
        val query = sql.selectFrom(W).where(fence(lease))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun targets(
        checkpoint: CollaborationReadinessCheckpointData,
        limit: Int,
    ): List<CollaborationReadinessControlTargetsRecord> {
        val lease = checkpoint.lease
        return sql.selectFrom(T)
            .where(
                T.EVENT_ID.eq(lease.eventId),
                T.COMPANY_ID.eq(lease.companyId),
                T.PUBLISHER_ACCOUNT_ID.eq(lease.publisherAccountId),
                T.PUBLISHER_DEVICE_ID.eq(lease.publisherDeviceId),
                T.LEASE_OWNER.eq(lease.ownerId),
                T.LEASE_TOKEN.eq(lease.token),
                T.LEASE_UNTIL.eq(lease.until),
                T.PUBLICATION_CUTOFF.eq(checkpoint.publicationCutoff),
                T.AFTER_ACCOUNT_ID.isNotDistinctFrom(checkpoint.afterAccountId),
                T.AFTER_DEVICE_ID.isNotDistinctFrom(checkpoint.afterDeviceId),
            )
            .orderBy(T.TARGET_ACCOUNT_ID.asc(), T.TARGET_DEVICE_ID.asc())
            .limit(limit)
            .fetch()
    }

    override fun advance(
        checkpoint: CollaborationReadinessCheckpointData,
        accountId: UUID,
        deviceId: UUID,
    ): Boolean =
        sql.update(W)
            .set(W.AFTER_ACCOUNT_ID, accountId)
            .set(W.AFTER_DEVICE_ID, deviceId)
            .set(W.ATTEMPTS, 0)
            .where(fence(checkpoint))
            .and(
                W.AFTER_ACCOUNT_ID.isNull.or(
                    DSL.row(W.AFTER_ACCOUNT_ID, W.AFTER_DEVICE_ID).lt(DSL.row(accountId, deviceId))
                )
            )
            .execute() == 1

    override fun release(
        checkpoint: CollaborationReadinessCheckpointData,
        complete: Boolean,
        availableAt: OffsetDateTime,
    ): Boolean =
        sql.update(W)
            .set(W.AVAILABLE_AT, availableAt)
            .set(
                W.COMPLETED_AT,
                if (complete) databaseNow() else DSL.`val`(null, OffsetDateTime::class.java),
            )
            .setNull(W.LEASE_OWNER)
            .setNull(W.LEASE_TOKEN)
            .setNull(W.LEASE_UNTIL)
            .where(fence(checkpoint))
            .execute() == 1

    private fun databaseNow() = DSL.field("clock_timestamp()", OffsetDateTime::class.java)

    private fun fence(checkpoint: CollaborationReadinessCheckpointData): Condition =
        fence(checkpoint.lease)
            .and(W.PUBLICATION_CUTOFF.eq(checkpoint.publicationCutoff))
            .and(W.AFTER_ACCOUNT_ID.isNotDistinctFrom(checkpoint.afterAccountId))
            .and(W.AFTER_DEVICE_ID.isNotDistinctFrom(checkpoint.afterDeviceId))

    private fun fence(lease: CollaborationReadinessLeaseData): Condition =
        DSL.and(
            W.EVENT_ID.eq(lease.eventId),
            W.COMPANY_ID.eq(lease.companyId),
            W.PUBLISHER_ACCOUNT_ID.eq(lease.publisherAccountId),
            W.PUBLISHER_DEVICE_ID.eq(lease.publisherDeviceId),
            W.LEASE_OWNER.eq(lease.ownerId),
            W.LEASE_TOKEN.eq(lease.token),
            W.LEASE_UNTIL.eq(lease.until),
            W.LEASE_UNTIL.gt(databaseNow()),
            W.COMPLETED_AT.isNull,
        )
}
