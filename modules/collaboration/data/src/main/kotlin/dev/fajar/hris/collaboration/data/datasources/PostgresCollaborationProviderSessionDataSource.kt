package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationProviderSessions.COLLABORATION_PROVIDER_SESSIONS as S
import dev.fajar.hris.schema.tables.records.CollaborationProviderSessionsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationProviderSessionDataSource(private val sql: DSLContext) :
    CollaborationProviderSessionDataSource {
    override fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): CollaborationProviderSessionsRecord? =
        sql.selectFrom(S)
            .where(S.COMPANY_ID.eq(companyId), S.ACCOUNT_ID.eq(accountId), S.DEVICE_ID.eq(deviceId))
            .forShare()
            .fetchOne()

    override fun insert(record: CollaborationProviderSessionsRecord) {
        sql.insertInto(S).set(record).execute()
    }

    override fun replace(
        record: CollaborationProviderSessionsRecord,
        expectedGeneration: Long,
    ): Boolean =
        sql.update(S)
            .set(S.TOKEN_ENCRYPTED, record.tokenEncrypted)
            .set(S.GENERATION, record.generation)
            .set(S.UPDATED_AT, record.updatedAt)
            .where(
                S.COMPANY_ID.eq(record.companyId),
                S.ACCOUNT_ID.eq(record.accountId),
                S.DEVICE_ID.eq(record.deviceId),
                S.GENERATION.eq(expectedGeneration),
                S.MATRIX_USER_ID.eq(record.matrixUserId),
                S.MATRIX_DEVICE_ID.eq(record.matrixDeviceId),
                S.CREATED_AT.eq(record.createdAt),
                S.CLEARED_AT.isNull,
            )
            .execute() == 1

    override fun clear(
        companyId: UUID,
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
                S.COMPANY_ID.eq(companyId),
                S.ACCOUNT_ID.eq(accountId),
                S.DEVICE_ID.eq(deviceId),
                S.GENERATION.eq(expectedGeneration),
                S.CLEARED_AT.isNull,
            )
            .execute() == 1
}
