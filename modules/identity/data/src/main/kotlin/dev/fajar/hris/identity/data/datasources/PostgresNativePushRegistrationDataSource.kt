package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.tables.NativePushRegistrations.NATIVE_PUSH_REGISTRATIONS as R
import dev.fajar.hris.schema.tables.records.NativePushRegistrationsRecord
import java.util.UUID
import org.jooq.DSLContext

class PostgresNativePushRegistrationDataSource(private val sql: DSLContext) :
    NativePushRegistrationDataSource {
    override fun find(accountId: UUID, sessionId: UUID): NativePushRegistrationsRecord? =
        sql.selectFrom(R)
            .where(R.ACCOUNT_ID.eq(accountId))
            .and(R.SESSION_ID.eq(sessionId))
            .fetchOne()

    override fun save(record: NativePushRegistrationsRecord, expectedVersion: Long?): Int =
        if (expectedVersion == null) sql.insertInto(R).set(record).execute()
        else
            sql.update(R)
                .set(record)
                .where(R.SESSION_ID.eq(record.sessionId))
                .and(R.ACCOUNT_ID.eq(record.accountId))
                .and(R.VERSION.eq(expectedVersion))
                .execute()

    override fun listEnabled(
        accountId: UUID,
        registeredBefore: java.time.Instant,
        after: UUID?,
        limit: Int,
    ): List<NativePushRegistrationsRecord> =
        sql.selectFrom(R)
            .where(
                R.ACCOUNT_ID.eq(accountId),
                R.ENABLED.isTrue,
                R.REGISTERED_AT.le(registeredBefore.atOffset(java.time.ZoneOffset.UTC)),
                after?.let { R.SESSION_ID.gt(it) } ?: org.jooq.impl.DSL.noCondition(),
            )
            .orderBy(R.SESSION_ID)
            .limit(limit)
            .fetch()
}
