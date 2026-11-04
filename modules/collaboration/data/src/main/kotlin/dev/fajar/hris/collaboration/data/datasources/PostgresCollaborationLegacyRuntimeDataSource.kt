package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationLegacyRuntime.COLLABORATION_LEGACY_RUNTIME as R
import dev.fajar.hris.schema.tables.CollaborationLegacyRuntimeLeases.COLLABORATION_LEGACY_RUNTIME_LEASES as L
import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeLeasesRecord
import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeRecord
import java.time.OffsetDateTime
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationLegacyRuntimeDataSource(private val sql: DSLContext) :
    CollaborationLegacyRuntimeDataSource {
    override fun insertIfAbsent(row: CollaborationLegacyRuntimeRecord) {
        sql.insertInto(R).set(row).onConflict(R.COMPANY_ID, R.DEVICE_ID).doNothing().execute()
    }

    override fun find(
        owner: CollaborationLegacyRuntimeCoordinatesData,
        lock: Boolean,
    ): CollaborationLegacyRuntimeRecord? {
        val query =
            sql.selectFrom(R)
                .where(
                    R.COMPANY_ID.eq(owner.companyId),
                    R.DEVICE_ID.eq(owner.deviceId),
                    R.ACCOUNT_ID.eq(owner.accountId),
                    R.NATIVE_SESSION_ID.eq(owner.nativeSessionId),
                )
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun slots(
        owner: CollaborationLegacyRuntimeCoordinatesData
    ): List<CollaborationLegacyRuntimeLeasesRecord> =
        sql.selectFrom(L)
            .where(
                L.COMPANY_ID.eq(owner.companyId),
                L.DEVICE_ID.eq(owner.deviceId),
                L.ACCOUNT_ID.eq(owner.accountId),
                L.NATIVE_SESSION_ID.eq(owner.nativeSessionId),
            )
            .orderBy(L.SLOT)
            .limit(9)
            .fetch()

    override fun claim(
        owner: CollaborationLegacyRuntimeCoordinatesData,
        until: OffsetDateTime,
    ): List<CollaborationLegacyRuntimeLeasesRecord> =
        sql.fetch(
                "select * from claim_collaboration_legacy_runtime(?,?,?,?,?::timestamptz)",
                owner.companyId,
                owner.deviceId,
                owner.accountId,
                owner.nativeSessionId,
                until,
            )
            .into(CollaborationLegacyRuntimeLeasesRecord::class.java)

    override fun release(row: CollaborationLegacyRuntimeLeasesRecord): Boolean =
        sql.update(L)
            .set(L.RELEASED_AT, DSL.field("clock_timestamp()", L.RELEASED_AT.dataType))
            .where(
                L.COMPANY_ID.eq(row.companyId),
                L.DEVICE_ID.eq(row.deviceId),
                L.ACCOUNT_ID.eq(row.accountId),
                L.NATIVE_SESSION_ID.eq(row.nativeSessionId),
                L.SLOT.eq(row.slot),
                L.RUNTIME_VERSION.eq(row.runtimeVersion),
                L.TOKEN.eq(row.token),
                L.STARTED_AT.eq(row.startedAt),
                L.EXPIRES_AT.eq(row.expiresAt),
                L.RELEASED_AT.isNull,
            )
            .execute() == 1

    override fun freeze(row: CollaborationLegacyRuntimeRecord, expectedVersion: Long): Boolean =
        sql.update(R)
            .set(R.PHASE, "DRAINING")
            .set(R.VERSION, expectedVersion + 1)
            .set(R.FROZEN_AT, row.frozenAt)
            .set(R.TARGET_SPACE_ID, row.targetSpaceId)
            .set(R.TARGET_DEVICE_ID, row.targetDeviceId)
            .where(
                R.COMPANY_ID.eq(row.companyId),
                R.DEVICE_ID.eq(row.deviceId),
                R.ACCOUNT_ID.eq(row.accountId),
                R.NATIVE_SESSION_ID.eq(row.nativeSessionId),
                R.PHASE.eq("LEGACY"),
                R.VERSION.eq(expectedVersion),
            )
            .execute() == 1
}
