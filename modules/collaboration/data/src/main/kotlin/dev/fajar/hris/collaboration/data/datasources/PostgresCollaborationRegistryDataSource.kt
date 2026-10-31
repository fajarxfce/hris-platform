package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.collaboration.data.models.BoundCollaborationDeviceData
import dev.fajar.hris.schema.tables.Accounts.ACCOUNTS as A
import dev.fajar.hris.schema.tables.CollaborationDeviceSessions.COLLABORATION_DEVICE_SESSIONS as B
import dev.fajar.hris.schema.tables.CollaborationDevices.COLLABORATION_DEVICES as D
import dev.fajar.hris.schema.tables.NativeSessions.NATIVE_SESSIONS as S
import dev.fajar.hris.schema.tables.records.CollaborationDeviceSessionsRecord
import dev.fajar.hris.schema.tables.records.CollaborationDevicesRecord
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationRegistryDataSource(private val sql: DSLContext) :
    CollaborationRegistryDataSource {
    override fun findBound(
        companyId: UUID,
        accountId: UUID,
        nativeSessionId: UUID,
    ): BoundCollaborationDeviceData? =
        sql.select(*D.fields(), B.NATIVE_SESSION_ID)
            .from(D)
            .join(B)
            .on(B.COMPANY_ID.eq(D.COMPANY_ID), B.DEVICE_ID.eq(D.ID), B.ACCOUNT_ID.eq(D.ACCOUNT_ID))
            .where(
                D.COMPANY_ID.eq(companyId),
                D.ACCOUNT_ID.eq(accountId),
                B.NATIVE_SESSION_ID.eq(nativeSessionId),
            )
            .forShare()
            // Bindings have no UPDATE policy. Locking them would also apply
            // that policy to this SELECT and hide otherwise readable rows.
            // The admission use case locks the parent session before this read.
            .of(D)
            .fetchOne()
            ?.let {
                BoundCollaborationDeviceData(
                    it.into(D),
                    requireNotNull(it.get(B.NATIVE_SESSION_ID)),
                )
            }

    override fun countActiveParentSessions(accountId: UUID, at: Instant): Int {
        val time = at.atOffset(ZoneOffset.UTC)
        return sql.select(DSL.countDistinct(B.NATIVE_SESSION_ID))
            .from(B)
            .join(D)
            .on(D.COMPANY_ID.eq(B.COMPANY_ID), D.ID.eq(B.DEVICE_ID), D.ACCOUNT_ID.eq(B.ACCOUNT_ID))
            .join(S)
            .on(S.ID.eq(B.NATIVE_SESSION_ID), S.ACCOUNT_ID.eq(B.ACCOUNT_ID))
            .join(A)
            .on(A.ID.eq(B.ACCOUNT_ID))
            .where(
                B.ACCOUNT_ID.eq(accountId),
                D.STATE.ne("REVOKED"),
                D.REVOKED_AT.isNull,
                S.REVOKED_AT.isNull,
                S.EXPIRES_AT.gt(time),
                S.CREATED_AT.le(time),
                S.CREDENTIAL_VERSION.eq(A.SECURITY_VERSION),
                A.ACTIVE.isTrue,
            )
            .fetchOne(0, Int::class.java) ?: 0
    }

    override fun insertDevice(record: CollaborationDevicesRecord) {
        sql.insertInto(D).set(record).execute()
    }

    override fun insertBinding(record: CollaborationDeviceSessionsRecord) {
        sql.insertInto(B).set(record).execute()
    }
}
