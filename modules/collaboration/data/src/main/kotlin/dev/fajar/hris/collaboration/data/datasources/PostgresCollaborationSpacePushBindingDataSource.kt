package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpacePushBindings.COLLABORATION_SPACE_PUSH_BINDINGS as P
import dev.fajar.hris.schema.tables.records.CollaborationSpacePushBindingsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationSpacePushBindingDataSource(private val sql: DSLContext) :
    CollaborationSpacePushBindingDataSource {
    override fun lock(spaceId: UUID, deviceId: UUID) {
        sql.execute(
            "select pg_advisory_xact_lock(hashtextextended(?,0))",
            "collaboration-space-push:$spaceId:$deviceId",
        )
    }

    override fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): CollaborationSpacePushBindingsRecord? =
        sql.selectFrom(P)
            .where(P.SPACE_ID.eq(spaceId), P.ACCOUNT_ID.eq(accountId), P.DEVICE_ID.eq(deviceId))
            .fetchOne()

    override fun byKey(hash: String): CollaborationSpacePushBindingsRecord? {
        require(hash.matches(Regex("[a-f0-9]{64}")))
        val previous =
            sql.select(
                    DSL.field(
                        "current_setting('app.collaboration_space_push_lookup',true)",
                        String::class.java,
                    )
                )
                .fetchOne(0, String::class.java)
                .orEmpty()
        var failure: Throwable? = null
        try {
            lookup(hash)
            return sql.selectFrom(P).where(P.KEY_HASH.eq(hash)).fetchOne()
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                lookup(previous)
            } catch (cleanup: Throwable) {
                if (failure == null) throw cleanup
                failure.addSuppressed(cleanup)
            }
        }
    }

    private fun lookup(value: String) {
        sql.select(
                DSL.field(
                    "set_config('app.collaboration_space_push_lookup',{0},true)",
                    String::class.java,
                    DSL.`val`(value),
                )
            )
            .fetch()
    }

    override fun insert(record: CollaborationSpacePushBindingsRecord) {
        check(sql.insertInto(P).set(record).execute() == 1)
    }

    override fun publish(
        expected: CollaborationSpacePushBindingsRecord,
        at: OffsetDateTime,
    ): Boolean =
        sql.update(P)
            .set(P.VERSION, 1L)
            .set(P.PUBLISHED_AT, at)
            .where(
                P.SPACE_ID.eq(expected.spaceId),
                P.ACCOUNT_ID.eq(expected.accountId),
                P.DEVICE_ID.eq(expected.deviceId),
                P.NATIVE_SESSION_ID.eq(expected.nativeSessionId),
                P.MATRIX_USER_ID.eq(expected.matrixUserId),
                P.MATRIX_DEVICE_ID.eq(expected.matrixDeviceId),
                P.KEY_HASH.eq(expected.keyHash),
                P.KEY_ENCRYPTED.eq(expected.keyEncrypted),
                P.RESERVED_AT.eq(expected.reservedAt),
                P.VERSION.eq(0L),
                P.PUBLISHED_AT.isNull,
            )
            .execute() == 1
}
