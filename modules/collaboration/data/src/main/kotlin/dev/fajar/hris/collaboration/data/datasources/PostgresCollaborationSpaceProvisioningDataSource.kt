package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpaceProvisioning.COLLABORATION_SPACE_PROVISIONING as Q
import dev.fajar.hris.schema.tables.records.CollaborationSpaceProvisioningRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationSpaceProvisioningDataSource(private val sql: DSLContext) :
    CollaborationSpaceProvisioningDataSource {
    override fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): CollaborationSpaceProvisioningRecord? {
        val query =
            sql.selectFrom(Q)
                .where(Q.SPACE_ID.eq(spaceId), Q.ACCOUNT_ID.eq(accountId), Q.DEVICE_ID.eq(deviceId))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun insert(row: CollaborationSpaceProvisioningRecord) {
        sql.insertInto(Q).set(row).execute()
    }

    override fun retry(row: CollaborationSpaceProvisioningRecord, expectedRevision: Long): Boolean =
        sql.update(Q)
            .set(Q.REVISION, row.revision)
            .set(Q.NATIVE_SESSION_VERSION, row.nativeSessionVersion)
            .set(Q.CREDENTIAL_VERSION, row.credentialVersion)
            .set(Q.AUTHENTICATED_AT, row.authenticatedAt)
            .set(Q.MFA_VERIFIED_AT, row.mfaVerifiedAt)
            .set(Q.CORRELATION_ID, row.correlationId)
            .set(Q.CLIENT_PLATFORM, row.clientPlatform)
            .set(Q.CLIENT_BUILD, row.clientBuild)
            .set(Q.REQUESTED_AT, row.requestedAt)
            .set(Q.EXPIRES_AT, row.expiresAt)
            .set(Q.AVAILABLE_AT, row.requestedAt)
            .set(Q.STATE, "QUEUED")
            .set(Q.ATTEMPTS, 0)
            .setNull(Q.COMPLETED_AT)
            .setNull(Q.FAILURE_CODE)
            .setNull(Q.LEASE_OWNER)
            .setNull(Q.LEASE_TOKEN)
            .setNull(Q.LEASE_UNTIL)
            .where(
                Q.SPACE_ID.eq(row.spaceId),
                Q.ACCOUNT_ID.eq(row.accountId),
                Q.DEVICE_ID.eq(row.deviceId),
                Q.NATIVE_SESSION_ID.eq(row.nativeSessionId),
                Q.REVISION.eq(expectedRevision),
                Q.STATE.`in`("SUCCEEDED", "FAILED", "CANCELLED"),
            )
            .execute() == 1

    override fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
    ): List<CollaborationSpaceProvisioningRecord> =
        sql.fetch(
                "select * from claim_collaboration_space_provisioning(?,?,?)",
                ownerId,
                limit,
                leaseSeconds,
            )
            .into(CollaborationSpaceProvisioningRecord::class.java)

    override fun finish(lease: CollaborationSpaceProvisioningLeaseData, failure: String?): Boolean {
        val state = if (failure == null) "SUCCEEDED" else "FAILED"
        return sql.query(
                """
                with stamp as materialized(select clock_timestamp() at_time)
                update collaboration_space_provisioning set state={0},completed_at=stamp.at_time,failure_code={1},
                    last_outcome_revision=revision,last_outcome_state={0},last_outcome_at=stamp.at_time,last_failure_code={1},
                    lease_owner=null,lease_token=null,lease_until=null from stamp
                where {2}
                """
                    .trimIndent(),
                DSL.`val`(state, Q.STATE.dataType),
                DSL.`val`(failure, Q.FAILURE_CODE.dataType),
                fence(lease),
            )
            .execute() == 1
    }

    override fun defer(lease: CollaborationSpaceProvisioningLeaseData, delaySeconds: Int): Boolean =
        sql.update(Q)
            .set(Q.STATE, "QUEUED")
            .set(
                Q.AVAILABLE_AT,
                DSL.least(
                    DSL.field(
                        "clock_timestamp()+make_interval(secs=>{0})",
                        Q.AVAILABLE_AT.dataType,
                        DSL.`val`(delaySeconds),
                    ),
                    Q.EXPIRES_AT,
                ),
            )
            .setNull(Q.LEASE_OWNER)
            .setNull(Q.LEASE_TOKEN)
            .setNull(Q.LEASE_UNTIL)
            .where(fence(lease))
            .execute() == 1

    override fun cancel(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedRevision: Long,
    ): Boolean =
        sql.query(
                """
                with stamp as materialized(select clock_timestamp() at_time)
                update collaboration_space_provisioning set state='CANCELLED',completed_at=stamp.at_time,
                    last_outcome_revision=revision,last_outcome_state='CANCELLED',last_outcome_at=stamp.at_time,last_failure_code=null,
                    lease_owner=null,lease_token=null,lease_until=null from stamp
                where {0}
                """
                    .trimIndent(),
                DSL.and(
                    Q.SPACE_ID.eq(spaceId),
                    Q.ACCOUNT_ID.eq(accountId),
                    Q.DEVICE_ID.eq(deviceId),
                    Q.REVISION.eq(expectedRevision),
                    Q.STATE.`in`("QUEUED", "RUNNING"),
                ),
            )
            .execute() == 1

    private fun fence(lease: CollaborationSpaceProvisioningLeaseData): Condition =
        DSL.and(
            Q.SPACE_ID.eq(lease.spaceId),
            Q.ACCOUNT_ID.eq(lease.accountId),
            Q.DEVICE_ID.eq(lease.deviceId),
            Q.NATIVE_SESSION_ID.eq(lease.nativeSessionId),
            Q.REVISION.eq(lease.revision),
            Q.NATIVE_SESSION_VERSION.eq(lease.nativeSessionVersion),
            Q.CREDENTIAL_VERSION.eq(lease.credentialVersion),
            Q.AUTHENTICATED_AT.eq(lease.authenticatedAt),
            Q.MFA_VERIFIED_AT.isNotDistinctFrom(lease.mfaVerifiedAt),
            Q.CORRELATION_ID.eq(lease.correlationId),
            Q.CLIENT_PLATFORM.eq(lease.clientPlatform),
            Q.CLIENT_BUILD.eq(lease.clientBuild),
            Q.REQUESTED_AT.eq(lease.requestedAt),
            Q.EXPIRES_AT.eq(lease.expiresAt),
            Q.ATTEMPTS.eq(lease.attempt),
            Q.LEASE_OWNER.eq(lease.ownerId),
            Q.LEASE_TOKEN.eq(lease.token),
            Q.LEASE_UNTIL.eq(lease.until),
            Q.STATE.eq("RUNNING"),
            Q.LEASE_UNTIL.gt(DSL.field("clock_timestamp()", OffsetDateTime::class.java)),
        )
}
