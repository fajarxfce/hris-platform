package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationGatewaySessions.COLLABORATION_GATEWAY_SESSIONS as G
import dev.fajar.hris.schema.tables.records.CollaborationGatewaySessionsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationGatewaySessionDataSource(private val sql: DSLContext) :
    CollaborationGatewaySessionDataSource {
    override fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): CollaborationGatewaySessionsRecord? {
        val query =
            sql.selectFrom(G)
                .where(
                    G.COMPANY_ID.eq(companyId),
                    G.ACCOUNT_ID.eq(accountId),
                    G.DEVICE_ID.eq(deviceId),
                )
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun byAccess(hash: String): CollaborationGatewaySessionsRecord? {
        lookup(hash)
        return sql.selectFrom(G).where(G.ACCESS_HASH.eq(hash)).fetchOne()
    }

    override fun byRefresh(hash: String): CollaborationGatewaySessionsRecord? {
        lookup(hash)
        return sql.selectFrom(G)
            .where(G.REFRESH_HASH.eq(hash).or(G.PREVIOUS_REFRESH_HASH.eq(hash)))
            .fetchOne()
    }

    private fun lookup(hash: String) {
        require(hash.matches(Regex("[a-f0-9]{64}")))
        sql.select(
                DSL.field(
                    "set_config('app.collaboration_gateway_lookup',{0},true)",
                    String::class.java,
                    DSL.`val`(hash),
                )
            )
            .fetch()
    }

    override fun insert(record: CollaborationGatewaySessionsRecord) {
        sql.insertInto(G).set(record).execute()
    }

    override fun replace(
        record: CollaborationGatewaySessionsRecord,
        expectedVersion: Long,
        expectedRefreshHash: String,
    ): Boolean =
        sql.update(G)
            .set(record)
            .where(
                G.COMPANY_ID.eq(record.companyId),
                G.ACCOUNT_ID.eq(record.accountId),
                G.DEVICE_ID.eq(record.deviceId),
                G.ID.eq(record.id),
                G.VERSION.eq(expectedVersion),
                G.REFRESH_HASH.eq(expectedRefreshHash),
                G.REVOKED_AT.isNull,
            )
            .execute() == 1

    override fun retire(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedVersion: Long,
        at: OffsetDateTime,
    ): Boolean =
        sql.update(G)
            .set(G.VERSION, G.VERSION.plus(1))
            .set(G.REVOKED_AT, at)
            .setNull(G.ACCESS_HASH)
            .setNull(G.REFRESH_HASH)
            .setNull(G.REPLAY_KIND)
            .setNull(G.BOOTSTRAP_OPERATION_ID)
            .setNull(G.PREVIOUS_REFRESH_HASH)
            .setNull(G.REPLAY_UNTIL)
            .setNull(G.REPLAY_ENCRYPTED)
            .where(
                G.COMPANY_ID.eq(companyId),
                G.ACCOUNT_ID.eq(accountId),
                G.DEVICE_ID.eq(deviceId),
                G.VERSION.eq(expectedVersion),
                G.REVOKED_AT.isNull,
            )
            .execute() == 1

    override fun clearExpiredReplays(at: OffsetDateTime, limit: Int): Int {
        require(limit in 1..100)
        return sql.execute(
            """
            with expired as (
                select company_id,id from collaboration_gateway_sessions
                where replay_until<=cast(? as timestamptz) and replay_until<=clock_timestamp() and replay_encrypted is not null
                order by replay_until,company_id,id limit ? for update skip locked
            ) update collaboration_gateway_sessions s
                set replay_kind=null,bootstrap_operation_id=null,previous_refresh_hash=null,replay_encrypted=null
                from expired e where s.company_id=e.company_id and s.id=e.id
            """,
            at,
            limit,
        )
    }
}
