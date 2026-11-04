package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpaceRequestLeases.COLLABORATION_SPACE_REQUEST_LEASES as L
import dev.fajar.hris.schema.tables.records.CollaborationSpaceRequestLeasesRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationSpaceRequestDataSource(private val sql: DSLContext) :
    CollaborationSpaceRequestDataSource {
    override fun claim(
        owner: CollaborationSpaceRequestCoordinatesData,
        kind: String,
        gatewayId: UUID?,
        gatewayVersion: Long?,
        until: OffsetDateTime,
    ): List<CollaborationSpaceRequestLeasesRecord> =
        sql.fetch(
                "select * from claim_collaboration_space_request(?::uuid,?::uuid,?::uuid,?::uuid,?::varchar,?::uuid,?::bigint,?::timestamptz)",
                owner.spaceId,
                owner.accountId,
                owner.deviceId,
                owner.nativeSessionId,
                kind,
                gatewayId,
                gatewayVersion,
                until,
            )
            .into(L)

    override fun find(
        owner: CollaborationSpaceRequestCoordinatesData,
        slot: Int,
        lock: Boolean,
    ): CollaborationSpaceRequestLeasesRecord? {
        val query =
            sql.selectFrom(L)
                .where(
                    L.SPACE_ID.eq(owner.spaceId),
                    L.ACCOUNT_ID.eq(owner.accountId),
                    L.DEVICE_ID.eq(owner.deviceId),
                    L.NATIVE_SESSION_ID.eq(owner.nativeSessionId),
                    L.SLOT.eq(slot),
                )
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun release(lease: CollaborationSpaceRequestLeasesRecord): Boolean =
        sql.deleteFrom(L)
            .where(
                L.SPACE_ID.eq(lease.spaceId),
                L.ACCOUNT_ID.eq(lease.accountId),
                L.DEVICE_ID.eq(lease.deviceId),
                L.NATIVE_SESSION_ID.eq(lease.nativeSessionId),
                L.SLOT.eq(lease.slot),
                L.TOKEN.eq(lease.token),
                L.KIND.eq(lease.kind),
                L.GATEWAY_ID.isNotDistinctFrom(lease.gatewayId),
                L.GATEWAY_VERSION.isNotDistinctFrom(lease.gatewayVersion),
                L.STARTED_AT.eq(lease.startedAt),
                L.EXPIRES_AT.eq(lease.expiresAt),
            )
            .execute() == 1

    override fun clearExpired(at: OffsetDateTime, limit: Int): Int {
        require(limit in 1..100)
        return sql.execute(
            """
            with expired as (
                select space_id,device_id,slot from collaboration_space_request_leases
                where expires_at<=cast(? as timestamptz) and expires_at<=clock_timestamp()
                order by expires_at,space_id,device_id,slot limit ? for update skip locked
            ) delete from collaboration_space_request_leases l using expired e
                where l.space_id=e.space_id and l.device_id=e.device_id and l.slot=e.slot
            """,
            at,
            limit,
        )
    }
}
