package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationPushBindings.COLLABORATION_PUSH_BINDINGS as P
import dev.fajar.hris.schema.tables.records.CollaborationPushBindingsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationPushBindingDataSource(private val sql: DSLContext) :
    CollaborationPushBindingDataSource {
    override fun lock(companyId: UUID, deviceId: UUID) {
        sql.execute(
            "select pg_advisory_xact_lock(hashtextextended(?,0))",
            "collaboration-push:$companyId:$deviceId",
        )
    }

    override fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): CollaborationPushBindingsRecord? =
        sql.selectFrom(P)
            .where(P.COMPANY_ID.eq(companyId), P.ACCOUNT_ID.eq(accountId), P.DEVICE_ID.eq(deviceId))
            .fetchOne()

    override fun byKey(hash: String): CollaborationPushBindingsRecord? {
        require(hash.matches(Regex("[a-f0-9]{64}")))
        sql.select(
                DSL.field(
                    "set_config('app.collaboration_push_lookup',{0},true)",
                    String::class.java,
                    DSL.`val`(hash),
                )
            )
            .fetch()
        return sql.selectFrom(P).where(P.KEY_HASH.eq(hash)).fetchOne()
    }

    override fun insert(record: CollaborationPushBindingsRecord) {
        sql.insertInto(P).set(record).execute()
    }

    override fun publish(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        parentId: UUID,
        reservedAt: OffsetDateTime,
        at: OffsetDateTime,
    ): Boolean =
        sql.update(P)
            .set(P.VERSION, 1L)
            .set(P.PUBLISHED_AT, at)
            .where(
                P.COMPANY_ID.eq(companyId),
                P.ACCOUNT_ID.eq(accountId),
                P.DEVICE_ID.eq(deviceId),
                P.NATIVE_SESSION_ID.eq(parentId),
                P.RESERVED_AT.eq(reservedAt),
                P.VERSION.eq(0L),
            )
            .execute() == 1
}
