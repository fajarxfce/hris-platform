package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpaceKeyPublications.COLLABORATION_SPACE_KEY_PUBLICATIONS as P
import dev.fajar.hris.schema.tables.records.CollaborationSpaceKeyPublicationsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationSpaceKeyDataSource(private val sql: DSLContext) :
    CollaborationSpaceKeyDataSource {
    override fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): CollaborationSpaceKeyPublicationsRecord? =
        sql.selectFrom(P)
            .where(P.SPACE_ID.eq(spaceId), P.ACCOUNT_ID.eq(accountId), P.DEVICE_ID.eq(deviceId))
            .fetchOne()

    override fun insert(record: CollaborationSpaceKeyPublicationsRecord) {
        check(sql.insertInto(P).set(record).execute() == 1)
    }

    override fun publish(
        expected: CollaborationSpaceKeyPublicationsRecord,
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
                P.FINGERPRINT.eq(expected.fingerprint),
                P.RESERVED_AT.eq(expected.reservedAt),
                P.VERSION.eq(0L),
                P.PUBLISHED_AT.isNull,
            )
            .execute() == 1
}
