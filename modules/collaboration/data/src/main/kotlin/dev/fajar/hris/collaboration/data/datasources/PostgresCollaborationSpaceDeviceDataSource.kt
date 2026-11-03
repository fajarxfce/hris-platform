package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.Accounts.ACCOUNTS as A
import dev.fajar.hris.schema.tables.CollaborationDeviceSessions.COLLABORATION_DEVICE_SESSIONS as LB
import dev.fajar.hris.schema.tables.CollaborationDevices.COLLABORATION_DEVICES as LD
import dev.fajar.hris.schema.tables.CollaborationSpaceDeviceSessions.COLLABORATION_SPACE_DEVICE_SESSIONS as B
import dev.fajar.hris.schema.tables.CollaborationSpaceDevices.COLLABORATION_SPACE_DEVICES as D
import dev.fajar.hris.schema.tables.CollaborationSpaceDirectory.COLLABORATION_SPACE_DIRECTORY as SPACE
import dev.fajar.hris.schema.tables.NativeSessions.NATIVE_SESSIONS as S
import dev.fajar.hris.schema.tables.records.CollaborationSpaceDeviceSessionsRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceDevicesRecord
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationSpaceDeviceDataSource(private val sql: DSLContext) :
    CollaborationSpaceDeviceDataSource {
    override fun findBound(
        spaceId: UUID,
        accountId: UUID,
        nativeSessionId: UUID,
    ): CollaborationSpaceDevicesRecord? =
        sql.select(*D.fields())
            .from(D)
            .join(B)
            .on(
                B.SPACE_ID.eq(D.SPACE_ID),
                B.DEVICE_ID.eq(D.ID),
                B.ACCOUNT_ID.eq(D.ACCOUNT_ID),
                B.NATIVE_SESSION_ID.eq(D.ORIGIN_NATIVE_SESSION_ID),
            )
            .where(
                D.SPACE_ID.eq(spaceId),
                D.ACCOUNT_ID.eq(accountId),
                B.NATIVE_SESSION_ID.eq(nativeSessionId),
            )
            .limit(1)
            .fetchOne()
            ?.into(D)

    override fun otherActiveParents(
        spaceId: UUID,
        accountId: UUID,
        excluding: UUID,
        at: Instant,
    ): Int? {
        val legacy =
            DSL.exists(
                sql.selectOne()
                    .from(LB)
                    .join(LD)
                    .on(
                        LD.COMPANY_ID.eq(LB.COMPANY_ID),
                        LD.ID.eq(LB.DEVICE_ID),
                        LD.ACCOUNT_ID.eq(LB.ACCOUNT_ID),
                    )
                    .where(
                        LB.ACCOUNT_ID.eq(accountId),
                        LB.NATIVE_SESSION_ID.eq(S.ID),
                        LD.STATE.ne("REVOKED"),
                        LD.REVOKED_AT.isNull,
                    )
            )
        val group =
            DSL.exists(
                sql.selectOne()
                    .from(B)
                    .join(D)
                    .on(
                        D.SPACE_ID.eq(B.SPACE_ID),
                        D.ID.eq(B.DEVICE_ID),
                        D.ACCOUNT_ID.eq(B.ACCOUNT_ID),
                        D.ORIGIN_NATIVE_SESSION_ID.eq(B.NATIVE_SESSION_ID),
                    )
                    .where(
                        B.SPACE_ID.eq(spaceId),
                        B.ACCOUNT_ID.eq(accountId),
                        B.NATIVE_SESSION_ID.eq(S.ID),
                        D.STATE.ne("REVOKED"),
                        D.REVOKED_AT.isNull,
                    )
            )
        val time = at.atOffset(ZoneOffset.UTC)
        // One row per parent, even if legacy company devices and its group device coexist.
        // Access-token expiry does not free quota while the refresh parent remains valid.
        val parents =
            sql.select(S.ID)
                .from(S)
                .join(A)
                .on(A.ID.eq(S.ACCOUNT_ID))
                .where(
                    S.ACCOUNT_ID.eq(accountId),
                    S.ID.ne(excluding),
                    S.REVOKED_AT.isNull,
                    S.EXPIRES_AT.gt(time),
                    S.CREATED_AT.le(time),
                    S.CREDENTIAL_VERSION.eq(A.SECURITY_VERSION),
                    A.ACTIVE.isTrue,
                    legacy.or(group),
                )
                .limit(6)
                .asTable("quota_parents")
        // Availability and the bounded count share one statement snapshot. A scope flap
        // must not hide group devices and be misinterpreted as zero active parents.
        return sql.select(DSL.field(sql.selectCount().from(parents)))
            .from(SPACE)
            .where(
                SPACE.SPACE_ID.eq(spaceId),
                DSL.field("current_actor_id()", UUID::class.java).eq(accountId),
            )
            .fetchOne()
            ?.value1()
    }

    override fun insertDevice(row: CollaborationSpaceDevicesRecord) {
        sql.insertInto(D).set(row).execute()
    }

    override fun insertBinding(row: CollaborationSpaceDeviceSessionsRecord) {
        sql.insertInto(B).set(row).execute()
    }
}
