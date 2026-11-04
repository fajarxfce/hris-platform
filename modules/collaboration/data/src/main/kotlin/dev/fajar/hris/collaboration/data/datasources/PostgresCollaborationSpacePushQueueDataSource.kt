package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpacePushHints.COLLABORATION_SPACE_PUSH_HINTS as H
import dev.fajar.hris.schema.tables.records.CollaborationSpacePushHintsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationSpacePushQueueDataSource(private val sql: DSLContext) :
    CollaborationSpacePushQueueDataSource {
    override fun append(
        spaceId: UUID,
        deviceId: UUID,
        accountId: UUID,
        parentId: UUID,
        roomId: String?,
        eventId: String?,
        eventHash: String?,
        lifetimeSeconds: Int,
    ): Boolean =
        sql.select(
                DSL.field(
                    "append_collaboration_space_push_hint({0},{1},{2},{3},{4},{5},{6},{7})",
                    Boolean::class.java,
                    DSL.`val`(spaceId),
                    DSL.`val`(deviceId),
                    DSL.`val`(accountId),
                    DSL.`val`(parentId),
                    DSL.`val`(roomId),
                    DSL.`val`(eventId),
                    DSL.`val`(eventHash),
                    DSL.`val`(lifetimeSeconds),
                )
            )
            .fetchOne(0, Boolean::class.java) == true

    override fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): CollaborationSpacePushHintsRecord? {
        val query =
            sql.selectFrom(H)
                .where(H.SPACE_ID.eq(spaceId), H.ACCOUNT_ID.eq(accountId), H.DEVICE_ID.eq(deviceId))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): List<CollaborationSpacePushHintsRecord> =
        sql.selectFrom(
                DSL.table(
                    "claim_collaboration_space_push_hints({0},{1},{2},{3})",
                    DSL.`val`(ownerId),
                    DSL.`val`(limit),
                    DSL.`val`(leaseSeconds),
                    DSL.`val`(maximumAttempts),
                )
            )
            .fetchInto(CollaborationSpacePushHintsRecord::class.java)

    override fun finish(lease: CollaborationSpacePushLeaseData): Boolean =
        sql.update(H)
            .set(H.DELIVERED_REVISION, lease.revision)
            .set(H.AVAILABLE_AT, databaseNow())
            .setNull(H.LEASE_OWNER)
            .setNull(H.LEASE_TOKEN)
            .setNull(H.LEASE_UNTIL)
            .setNull(H.LEASE_REVISION)
            .where(fence(lease))
            .execute() == 1

    override fun defer(
        lease: CollaborationSpacePushLeaseData,
        availableAt: OffsetDateTime,
    ): Boolean =
        sql.update(H)
            .set(
                H.AVAILABLE_AT,
                DSL.`when`(H.REVISION.gt(lease.revision), databaseNow())
                    .otherwise(DSL.`val`(availableAt)),
            )
            .setNull(H.LEASE_OWNER)
            .setNull(H.LEASE_TOKEN)
            .setNull(H.LEASE_UNTIL)
            .setNull(H.LEASE_REVISION)
            .where(fence(lease))
            .execute() == 1

    private fun databaseNow() = DSL.field("clock_timestamp()", OffsetDateTime::class.java)

    private fun fence(lease: CollaborationSpacePushLeaseData): Condition =
        DSL.and(
            H.SPACE_ID.eq(lease.spaceId),
            H.ACCOUNT_ID.eq(lease.accountId),
            H.DEVICE_ID.eq(lease.deviceId),
            H.NATIVE_SESSION_ID.eq(lease.nativeSessionId),
            H.LEASE_OWNER.eq(lease.ownerId),
            H.LEASE_TOKEN.eq(lease.token),
            H.LEASE_REVISION.eq(lease.revision),
            H.LEASE_UNTIL.eq(lease.until),
            H.LEASE_UNTIL.gt(databaseNow()),
        )
}
