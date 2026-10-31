package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationDeviceKeyPublications.COLLABORATION_DEVICE_KEY_PUBLICATIONS as P
import dev.fajar.hris.schema.tables.records.CollaborationDeviceKeyPublicationsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationDeviceKeyDataSource(private val sql: DSLContext) :
    CollaborationDeviceKeyDataSource {
    override fun lock(companyId: UUID, deviceId: UUID) {
        sql.execute(
            "select pg_advisory_xact_lock(hashtextextended(?,0))",
            "collaboration-device-keys:$companyId:$deviceId",
        )
    }

    override fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): CollaborationDeviceKeyPublicationsRecord? =
        sql.selectFrom(P)
            .where(P.COMPANY_ID.eq(companyId), P.ACCOUNT_ID.eq(accountId), P.DEVICE_ID.eq(deviceId))
            .fetchOne()

    override fun insert(record: CollaborationDeviceKeyPublicationsRecord) {
        sql.insertInto(P).set(record).execute()
    }

    override fun publish(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        fingerprint: String,
        reservedAt: OffsetDateTime,
        publishedAt: OffsetDateTime,
    ): Boolean =
        sql.update(P)
            .set(P.VERSION, 1L)
            .set(P.PUBLISHED_AT, publishedAt)
            .where(
                P.COMPANY_ID.eq(companyId),
                P.ACCOUNT_ID.eq(accountId),
                P.DEVICE_ID.eq(deviceId),
                P.FINGERPRINT.eq(fingerprint),
                P.RESERVED_AT.eq(reservedAt),
                P.VERSION.eq(0L),
            )
            .execute() == 1
}
