package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpaceReadinessDeliveries.COLLABORATION_SPACE_READINESS_DELIVERIES as W
import dev.fajar.hris.schema.tables.CollaborationSpaceReadinessTargets.COLLABORATION_SPACE_READINESS_TARGETS as T
import dev.fajar.hris.schema.tables.records.CollaborationSpaceReadinessDeliveriesRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceReadinessTargetsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationSpaceReadinessQueueDataSource(private val sql: DSLContext) :
    CollaborationSpaceReadinessQueueDataSource {
    override fun claim(
        channel: String,
        owner: UUID,
        limit: Int,
    ): List<CollaborationSpaceReadinessDeliveriesRecord> =
        sql.selectFrom(
                DSL.table(
                    "claim_collaboration_space_readiness({0}::varchar,{1},{2})",
                    DSL.`val`(channel),
                    DSL.`val`(owner),
                    DSL.`val`(limit),
                )
            )
            .fetchInto(CollaborationSpaceReadinessDeliveriesRecord::class.java)

    override fun find(
        lease: CollaborationSpaceReadinessLeaseData,
        lock: Boolean,
    ): CollaborationSpaceReadinessDeliveriesRecord? {
        val query = sql.selectFrom(W).where(fence(lease))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun read(
        eventId: UUID,
        channel: String,
    ): CollaborationSpaceReadinessDeliveriesRecord? =
        sql.selectFrom(W).where(W.EVENT_ID.eq(eventId), W.CHANNEL.eq(channel)).fetchOne()

    override fun targets(
        checkpoint: CollaborationSpaceReadinessCheckpointData,
        limit: Int,
    ): List<CollaborationSpaceReadinessTargetsRecord> {
        val lease = checkpoint.lease
        return sql.selectFrom(T)
            .where(
                T.EVENT_ID.eq(lease.eventId),
                T.CHANNEL.eq(lease.channel),
                T.SPACE_ID.eq(lease.spaceId),
                T.PUBLISHER_ACCOUNT_ID.eq(lease.publisherAccountId),
                T.PUBLISHER_DEVICE_ID.eq(lease.publisherDeviceId),
                T.LEASE_OWNER.eq(lease.ownerId),
                T.LEASE_TOKEN.eq(lease.token),
                T.LEASE_UNTIL.eq(lease.until),
                T.PUBLICATION_REVISION.eq(checkpoint.revision),
                T.PUBLICATION_CUTOFF.eq(checkpoint.cutoff),
                T.CAPTURED_AT.eq(checkpoint.capturedAt),
                T.EXPIRES_AT.eq(checkpoint.expiresAt),
                T.AVAILABLE_AT.eq(checkpoint.availableAt),
                T.ATTEMPTS.eq(checkpoint.attempts),
                T.AFTER_ACCOUNT_ID.isNotDistinctFrom(checkpoint.afterAccountId),
                T.AFTER_DEVICE_ID.isNotDistinctFrom(checkpoint.afterDeviceId),
            )
            .orderBy(T.TARGET_ACCOUNT_ID.asc(), T.TARGET_DEVICE_ID.asc())
            .limit(limit)
            .fetch()
    }

    override fun advance(
        checkpoint: CollaborationSpaceReadinessCheckpointData,
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
        checkpoint: CollaborationSpaceReadinessCheckpointData,
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

    private fun fence(checkpoint: CollaborationSpaceReadinessCheckpointData): Condition =
        fence(checkpoint.lease)
            .and(W.PUBLICATION_REVISION.eq(checkpoint.revision))
            .and(W.PUBLICATION_CUTOFF.eq(checkpoint.cutoff))
            .and(W.CAPTURED_AT.eq(checkpoint.capturedAt))
            .and(W.EXPIRES_AT.eq(checkpoint.expiresAt))
            .and(W.AVAILABLE_AT.eq(checkpoint.availableAt))
            .and(W.ATTEMPTS.eq(checkpoint.attempts))
            .and(W.AFTER_ACCOUNT_ID.isNotDistinctFrom(checkpoint.afterAccountId))
            .and(W.AFTER_DEVICE_ID.isNotDistinctFrom(checkpoint.afterDeviceId))

    private fun fence(lease: CollaborationSpaceReadinessLeaseData): Condition =
        DSL.and(
            W.EVENT_ID.eq(lease.eventId),
            W.CHANNEL.eq(lease.channel),
            W.SPACE_ID.eq(lease.spaceId),
            W.PUBLISHER_ACCOUNT_ID.eq(lease.publisherAccountId),
            W.PUBLISHER_DEVICE_ID.eq(lease.publisherDeviceId),
            W.LEASE_OWNER.eq(lease.ownerId),
            W.LEASE_TOKEN.eq(lease.token),
            W.LEASE_UNTIL.eq(lease.until),
            W.LEASE_UNTIL.gt(databaseNow()),
            W.EXPIRES_AT.gt(databaseNow()),
            W.COMPLETED_AT.isNull,
        )
}
