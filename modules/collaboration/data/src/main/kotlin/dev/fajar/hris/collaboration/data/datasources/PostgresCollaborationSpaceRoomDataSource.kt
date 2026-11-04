package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpaceRoomBindings.COLLABORATION_SPACE_ROOM_BINDINGS as B
import dev.fajar.hris.schema.tables.CollaborationSpaceRoomIntents.COLLABORATION_SPACE_ROOM_INTENTS as I
import dev.fajar.hris.schema.tables.CollaborationSpaceRoomProvisioning.COLLABORATION_SPACE_ROOM_PROVISIONING as Q
import dev.fajar.hris.schema.tables.CollaborationSpaceRoomStatus.COLLABORATION_SPACE_ROOM_STATUS as S
import dev.fajar.hris.schema.tables.records.*
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationSpaceRoomDataSource(private val sql: DSLContext) :
    CollaborationSpaceRoomDataSource {
    override fun lockOriginalPair(companyId: UUID, first: UUID, second: UUID) {
        sql.execute(
            "select pg_advisory_xact_lock(hashtextextended(?,0))",
            "collaboration.direct:$companyId:$first:$second",
        )
    }

    override fun lock(spaceId: UUID, conversationId: UUID) {
        sql.execute(
            "select pg_advisory_xact_lock(hashtextextended(?,0))",
            "collaboration-space-room:$spaceId:$conversationId",
        )
    }

    override fun intent(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
    ): CollaborationSpaceRoomIntentsRecord? =
        sql.selectFrom(I)
            .where(
                I.SPACE_ID.eq(spaceId),
                I.CONVERSATION_ID.eq(conversationId),
                I.FIRST_ACCOUNT_ID.eq(accountId).or(I.SECOND_ACCOUNT_ID.eq(accountId)),
            )
            .fetchOne()

    override fun insertIntent(row: CollaborationSpaceRoomIntentsRecord) {
        check(sql.insertInto(I).set(row).execute() == 1)
    }

    override fun binding(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
    ): CollaborationSpaceRoomBindingData? =
        sql.select(I.asterisk(), B.asterisk())
            .from(B)
            .join(I)
            .on(I.SPACE_ID.eq(B.SPACE_ID), I.CONVERSATION_ID.eq(B.CONVERSATION_ID))
            .where(
                B.SPACE_ID.eq(spaceId),
                B.CONVERSATION_ID.eq(conversationId),
                I.FIRST_ACCOUNT_ID.eq(accountId).or(I.SECOND_ACCOUNT_ID.eq(accountId)),
            )
            .fetchOne { CollaborationSpaceRoomBindingData(it.into(I), it.into(B)) }

    override fun boundRoom(
        spaceId: UUID,
        accountId: UUID,
        serverName: String,
        roomId: String,
    ): List<CollaborationSpaceRoomBindingData> =
        sql.select(I.asterisk(), B.asterisk())
            .from(B)
            .join(I)
            .on(I.SPACE_ID.eq(B.SPACE_ID), I.CONVERSATION_ID.eq(B.CONVERSATION_ID))
            .where(
                B.SPACE_ID.eq(spaceId),
                B.MATRIX_ROOM_ID.eq(roomId),
                I.MATRIX_SERVER_NAME.eq(serverName),
                I.FIRST_ACCOUNT_ID.eq(accountId).or(I.SECOND_ACCOUNT_ID.eq(accountId)),
            )
            .limit(2)
            .fetch { CollaborationSpaceRoomBindingData(it.into(I), it.into(B)) }

    override fun insertBinding(row: CollaborationSpaceRoomBindingsRecord) {
        check(sql.insertInto(B).set(row).execute() == 1)
    }

    override fun status(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
    ): List<CollaborationSpaceRoomStatusRecord> =
        sql.selectFrom(S)
            .where(
                S.SPACE_ID.eq(spaceId),
                S.CONVERSATION_ID.eq(conversationId),
                owns(spaceId, accountId, conversationId),
            )
            .limit(2)
            .fetch()

    private fun owns(spaceId: UUID, accountId: UUID, conversationId: UUID): Condition =
        DSL.exists(
            sql.selectOne()
                .from(I)
                .where(
                    I.SPACE_ID.eq(spaceId),
                    I.CONVERSATION_ID.eq(conversationId),
                    I.FIRST_ACCOUNT_ID.eq(accountId).or(I.SECOND_ACCOUNT_ID.eq(accountId)),
                )
        )

    override fun find(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
        lock: Boolean,
    ): CollaborationSpaceRoomProvisioningRecord? {
        val query =
            sql.selectFrom(Q)
                .where(
                    Q.SPACE_ID.eq(spaceId),
                    Q.ACCOUNT_ID.eq(accountId),
                    Q.CONVERSATION_ID.eq(conversationId),
                )
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun insert(row: CollaborationSpaceRoomProvisioningRecord) {
        check(sql.insertInto(Q).set(row).execute() == 1)
    }

    override fun retry(
        row: CollaborationSpaceRoomProvisioningRecord,
        expectedRevision: Long,
    ): Boolean =
        sql.update(Q)
            .set(Q.REVISION, row.revision)
            .set(Q.DEVICE_ID, row.deviceId)
            .set(Q.DEVICE_VERSION, row.deviceVersion)
            .set(Q.PROVIDER_GENERATION, row.providerGeneration)
            .set(Q.NATIVE_SESSION_ID, row.nativeSessionId)
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
                Q.CONVERSATION_ID.eq(row.conversationId),
                Q.REVISION.eq(expectedRevision),
                Q.STATE.`in`("SUCCEEDED", "FAILED", "CANCELLED"),
            )
            .execute() == 1

    override fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
    ): List<CollaborationSpaceRoomProvisioningRecord> =
        sql.fetch(
                "select * from claim_collaboration_space_room_provisioning(?,?,?)",
                ownerId,
                limit,
                leaseSeconds,
            )
            .into(CollaborationSpaceRoomProvisioningRecord::class.java)

    override fun finish(lease: CollaborationSpaceRoomLeaseData, failure: String?): Boolean {
        val state = if (failure == null) "SUCCEEDED" else "FAILED"
        return sql.query(
                """
                with stamp as materialized(select clock_timestamp() at_time)
                update collaboration_space_room_provisioning set state={0},completed_at=stamp.at_time,failure_code={1},
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

    override fun defer(lease: CollaborationSpaceRoomLeaseData, delaySeconds: Int): Boolean =
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
        conversationId: UUID,
        expectedRevision: Long,
    ): Boolean =
        sql.query(
                """
                with stamp as materialized(select clock_timestamp() at_time)
                update collaboration_space_room_provisioning set state='CANCELLED',completed_at=stamp.at_time,
                    last_outcome_revision=revision,last_outcome_state='CANCELLED',last_outcome_at=stamp.at_time,last_failure_code=null,
                    lease_owner=null,lease_token=null,lease_until=null from stamp
                where {0}
                """
                    .trimIndent(),
                DSL.and(
                    Q.SPACE_ID.eq(spaceId),
                    Q.ACCOUNT_ID.eq(accountId),
                    Q.CONVERSATION_ID.eq(conversationId),
                    Q.REVISION.eq(expectedRevision),
                    Q.STATE.`in`("QUEUED", "RUNNING"),
                ),
            )
            .execute() == 1

    private fun fence(lease: CollaborationSpaceRoomLeaseData): Condition =
        DSL.and(
            Q.SPACE_ID.eq(lease.spaceId),
            Q.CONVERSATION_ID.eq(lease.conversationId),
            Q.DEVICE_VERSION.eq(lease.deviceVersion),
            Q.PROVIDER_GENERATION.eq(lease.providerGeneration),
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
