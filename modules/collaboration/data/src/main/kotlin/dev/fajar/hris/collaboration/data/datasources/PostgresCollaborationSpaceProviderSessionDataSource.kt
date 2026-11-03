package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpaceProviderSessions.COLLABORATION_SPACE_PROVIDER_SESSIONS as S
import dev.fajar.hris.schema.tables.records.CollaborationSpaceProviderSessionsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationSpaceProviderSessionDataSource(private val sql: DSLContext) :
    CollaborationSpaceProviderSessionDataSource {
    override fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): CollaborationSpaceProviderSessionsRecord? =
        sql.selectFrom(S)
            .where(S.SPACE_ID.eq(spaceId), S.ACCOUNT_ID.eq(accountId), S.DEVICE_ID.eq(deviceId))
            .fetchOne()

    override fun insert(row: CollaborationSpaceProviderSessionsRecord) {
        sql.insertInto(S).set(row).execute()
    }

    override fun replace(
        row: CollaborationSpaceProviderSessionsRecord,
        expectedGeneration: Long,
    ): Boolean =
        sql.update(S)
            .set(S.TOKEN_ENCRYPTED, row.tokenEncrypted)
            .set(S.TOKEN_EXPIRES_AT, row.tokenExpiresAt)
            .set(S.GENERATION, row.generation)
            .set(S.UPDATED_AT, row.updatedAt)
            .where(
                S.SPACE_ID.eq(row.spaceId),
                S.ACCOUNT_ID.eq(row.accountId),
                S.DEVICE_ID.eq(row.deviceId),
                S.NATIVE_SESSION_ID.eq(row.nativeSessionId),
                S.MATRIX_USER_ID.eq(row.matrixUserId),
                S.MATRIX_DEVICE_ID.eq(row.matrixDeviceId),
                S.CREATED_AT.eq(row.createdAt),
                S.GENERATION.eq(expectedGeneration),
                S.CLEARED_AT.isNull,
            )
            .execute() == 1

    override fun clear(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedGeneration: Long,
        at: OffsetDateTime,
    ): Boolean =
        sql.update(S)
            .setNull(S.TOKEN_ENCRYPTED)
            .set(S.CLEARED_AT, at)
            .set(S.UPDATED_AT, at)
            .set(S.GENERATION, S.GENERATION.plus(1))
            .where(
                S.SPACE_ID.eq(spaceId),
                S.ACCOUNT_ID.eq(accountId),
                S.DEVICE_ID.eq(deviceId),
                S.GENERATION.eq(expectedGeneration),
                S.CLEARED_AT.isNull,
            )
            .execute() == 1
}
