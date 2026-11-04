package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.AccessNativeCollaborationSpaceMatrixDevice
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.postgresql.PostgreSQLContainer

/** Two pre-queue native devices upgraded to V135; requests use an ordinary runtime role. */
internal class SpaceKeyExchangeFixture(
    postgres: PostgreSQLContainer,
    beforeQueue: (SpaceGatewayPostgresFixture) -> Unit = {},
) {
    val s = SpaceGatewayPostgresFixture(postgres, ageSeconds = 120)
    val peer = UUID.randomUUID()
    val peerCompany = UUID.randomUUID()
    val peerParent = UUID.randomUUID()
    val peerDevice = UUID.randomUUID()
    val conversation = UUID.randomUUID()
    val pair = listOf(s.account, peer).sortedBy(UUID::toString)
    val peerActor = s.actor.copy(accountId = peer)
    val worker = JdbcTemplate(s.workerSource)
    val workerTransactions =
        PostgresTransactionRunner(JdbcTransactionManager(s.workerSource), worker)
    var boundRoom = "!fictional-room:matrix.example.test"

    init {
        s.owner.update(
            "insert into companies(id,code,name,timezone) values(?,?,'Fictional other company','UTC')",
            peerCompany,
            peerCompany.toString().take(12),
        )
        s.owner.update(
            "insert into accounts(id,email,display_name) values(?,?,'Fictional room peer')",
            peer,
            "$peer@example.test",
        )
        s.owner.update(
            "insert into company_memberships(company_id,account_id) values(?,?)",
            peerCompany,
            peer,
        )
        s.owner.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
            peerCompany,
            peer,
        )
        s.owner.update(
            "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,created_at) values(?,?,'matrix.example.test',?,'SPACE',?::timestamptz)",
            s.space,
            peer,
            user(peer),
            s.at.toString(),
        )
        s.owner.update(
            "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
            peerParent,
            peer,
            peerParent.toString().replace("-", "").repeat(2),
            s.actor.authenticatedAt.toString(),
            s.at.plusSeconds(3600).toString(),
            s.at.plusSeconds(900).toString(),
            s.at.minusSeconds(60).toString(),
            s.at.minusSeconds(30).toString(),
        )
        s.owner.update(
            "insert into collaboration_space_devices(space_id,id,account_id,matrix_user_id,matrix_device_id,origin,origin_native_session_id,platform,label,state,version,created_at,updated_at) values(?,?,?,?,?,'SPACE',?,'ANDROID','Fictional peer','PROVISIONING',0,?::timestamptz,?::timestamptz)",
            s.space,
            peerDevice,
            peer,
            user(peer),
            "HRISG_${peerDevice.toString().replace("-", "")}",
            peerParent,
            s.at.toString(),
            s.at.toString(),
        )
        s.owner.update(
            "insert into collaboration_space_device_sessions(space_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
            s.space,
            peerDevice,
            peer,
            peerParent,
            s.at.toString(),
        )
        s.migrate {
            s.owner.update(
                "insert into collaboration_space_provider_sessions(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,?,0,'owned-peer-fixture',?::timestamptz,?::timestamptz,?::timestamptz)",
                s.space,
                peerDevice,
                peer,
                peerParent,
                user(peer),
                "HRISG_${peerDevice.toString().replace("-", "")}",
                s.at.toString(),
                s.at.toString(),
                s.at.plusSeconds(600).toString(),
            )
            s.owner.update(
                "update collaboration_space_devices set state='ACTIVE',version=1 where id=?",
                peerDevice,
            )
            beforeQueue(s)
        }
        Flyway.configure().dataSource(s.source).target("135").load().migrate()
        s.owner.execute(
            "grant select,insert,update,delete on all tables in schema public to space_gateway_fixture,space_gateway_worker"
        )
        assertFalse(
            s.owner.queryForObject(
                "select pg_has_role('space_gateway_fixture','hris_worker_capability','member')",
                Boolean::class.java,
            )!!
        )
    }

    val base = SpaceGatewayBootstrapFixture(postgres, s)
    val recipients =
        StoredCollaborationSpaceRecipientRepository(
            PostgresCollaborationSpaceRecipientDataSource(base.sql)
        )
    val keys =
        StoredCollaborationSpaceKeyRepository(PostgresCollaborationSpaceKeyDataSource(base.sql))
    val providers =
        EncryptedCollaborationSpaceProviderSessionRepository(
            PostgresCollaborationSpaceProviderSessionDataSource(base.sql),
            JceIdentityTokenDataSource(s.currentKeys),
        )
    val requests =
        StoredCollaborationSpaceRequestRepository(
            PostgresCollaborationSpaceRequestDataSource(base.sql)
        )
    val pushBindings =
        EncryptedCollaborationSpacePushBindingRepository(
            PostgresCollaborationSpacePushBindingDataSource(base.sql),
            JceIdentityTokenDataSource(s.currentKeys),
        )
    val nativePush =
        dev.fajar.hris.identity.data.repositories.StoredNativePushRegistrationRepository(
            dev.fajar.hris.identity.data.datasources.PostgresNativePushRegistrationDataSource(
                base.sql
            ),
            JceIdentityTokenDataSource(s.currentKeys),
        )
    val ownKeys = MatrixKeyFixture(s.user, "HRISG_${compact(s.device)}")
    val peerKeys = MatrixKeyFixture(user(peer), "HRISG_${compact(peerDevice)}")
    val tokens: CollaborationSpaceGatewayTokens

    init {
        rotateProvider()
        base.clock.at = now()
        tokens = success(base.bootstrap(command = base.command(providers = providers)))
        success(
            s.tx(peerActor) {
                safeDatabaseCall {
                    s.runtime.update(
                        "insert into collaboration_space_direct_conversations(space_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?::timestamptz)",
                        s.space,
                        conversation,
                        pair[0],
                        pair[1],
                        peer,
                        s.at.toString(),
                    )
                }
            }
        )
    }

    fun rotateProvider(
        token: String = "fixture-owned-query-token",
        expiresAt: Instant = now().plusSeconds(300),
    ) {
        success(
            s.tx {
                providers.metadata(s.space, s.account, s.device).flatMap { current ->
                    val previous = requireNotNull(current)
                    providers.rotate(
                        CollaborationSpaceProviderSession(
                            previous.copy(
                                generation = previous.generation + 1,
                                updatedAt = now(),
                                expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS),
                            ),
                            token,
                        ),
                        previous.generation,
                    )
                }
            }
        )
    }

    fun reserve(account: UUID = s.account, confirmed: Boolean = true) {
        val own = account == s.account
        val material = if (own) ownKeys else peerKeys
        val row =
            CollaborationSpaceKeyPublication(
                s.space,
                account,
                if (own) s.device else peerDevice,
                if (own) s.parent else peerParent,
                material.userId,
                material.deviceId,
                success(
                    validateCollaborationDeviceKeys(
                        material.device,
                        material.userId,
                        material.deviceId,
                    )
                ),
                0,
                now(),
            )
        success(
            s.tx(s.actor.copy(accountId = account)) {
                keys.reserve(row).flatMap {
                    if (confirmed) keys.publish(row, now()).map { Unit } else Result.Success(Unit)
                }
            }
        )
    }

    fun confirmPeer() =
        success(
            s.tx(peerActor) {
                keys.find(s.space, peer, peerDevice).flatMap {
                    keys.publish(requireNotNull(it), now())
                }
            }
        )

    fun bind(roomId: String = boundRoom) {
        boundRoom = roomId
        val generation =
            success(s.tx { providers.metadata(s.space, s.account, s.device) })!!.generation
        success(
            s.tx {
                safeDatabaseCall {
                    intent()
                    enqueue(generation = generation)
                }
            }
        )
        val claimed = success(worker { claim() }).single()
        success(
            worker(s.actor) {
                publish(claimed)
                finish(claimed)
            }
        )
    }

    fun command(
        providerKeys: CollaborationMatrixKeyRepository,
        recipients: CollaborationSpaceRecipientRepository = this.recipients,
        identities: IdentityRepository = base.identities,
        clock: Clock = Clock.systemUTC(),
        toDevice: CollaborationMatrixToDeviceRepository =
            DisabledCollaborationMatrixToDeviceRepository(),
        rooms: CollaborationSpaceRoomRepository =
            StoredCollaborationSpaceRoomRepository(
                PostgresCollaborationSpaceRoomDataSource(base.sql)
            ),
        roomMessages: CollaborationMatrixRoomMessageRepository =
            DisabledCollaborationMatrixRoomMessageRepository(),
        roomHistory: CollaborationSpaceMatrixRoomHistoryRepository =
            DisabledCollaborationSpaceMatrixRoomHistoryRepository(),
        sync: CollaborationSpaceMatrixSyncRepository =
            DisabledCollaborationSpaceMatrixSyncRepository(),
        syncCursors: CollaborationSpaceSyncCursorRepository =
            EncryptedCollaborationSpaceSyncCursorRepository(collaborationCursorFixture()),
        roomAccess: CollaborationSpaceMatrixRoomAccessRepository =
            DisabledCollaborationSpaceMatrixRoomAccessRepository(),
        service: CollaborationServicePolicy = CollaborationServicePolicy(),
        pushBindings: CollaborationSpacePushBindingRepository = this.pushBindings,
        providerPush: CollaborationMatrixPusherRepository =
            DisabledCollaborationMatrixPusherRepository(),
        nativePush: dev.fajar.hris.identity.domain.repositories.NativePushRegistrationRepository =
            this.nativePush,
        journal: ChangeJournalRepository = base.journal,
    ) =
        AccessNativeCollaborationSpaceMatrixDevice(
            s.repo,
            requests,
            base.registry,
            providers,
            DisabledCollaborationMatrixRepository(),
            base.selections,
            base.legacy,
            base.completions,
            base.provisioning,
            base.spaces,
            base.sessions,
            identities,
            s.transactions,
            IdentitySecurityPolicy(),
            CollaborationSpacePolicy(true, 5, 5),
            CollaborationMatrixPolicy(true, "matrix.example.test"),
            clock,
            keys,
            providerKeys,
            journal,
            recipients,
            toDevice,
            rooms,
            roomMessages,
            roomHistory,
            StoredCollaborationSpaceConversationRepository(
                PostgresCollaborationSpaceConversationDataSource(base.sql)
            ),
            syncCursors,
            sync,
            roomAccess,
            service,
            pushBindings,
            providerPush,
            nativePush,
        )

    fun queryRequest() =
        CollaborationMatrixKeyQuery(mapOf(s.user to emptyList(), user(peer) to emptyList()))

    fun claimRequest() =
        CollaborationMatrixKeyClaim(
            mapOf(user(peer) to mapOf(peerKeys.deviceId to "signed_curve25519"))
        )

    fun query(
        provider: CollaborationMatrixKeyRepository = provider(),
        command: AccessNativeCollaborationSpaceMatrixDevice = command(provider),
        request: CollaborationMatrixKeyQuery = queryRequest(),
    ) = command.queryKeys(tokens.accessToken, UUID.randomUUID(), request)

    fun claimKeys(
        provider: CollaborationMatrixKeyRepository = provider(),
        command: AccessNativeCollaborationSpaceMatrixDevice = command(provider),
    ) = command.claimKeys(tokens.accessToken, UUID.randomUUID(), claimRequest())

    fun provider() = SpaceKeyProvider(this)

    fun revokePeer() =
        s.owner.update(
            "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
            peerCompany,
            peer,
        )

    fun grantPeer() =
        s.owner.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat') on conflict do nothing",
            peerCompany,
            peer,
        )

    fun leases() =
        s.owner.queryForObject(
            "select count(*) from collaboration_space_request_leases",
            Int::class.java,
        )!!

    fun provenance() =
        mapOf(
            "devices" to
                s.owner.queryForList("select * from collaboration_space_devices order by id"),
            "keys" to
                s.owner.queryForList(
                    "select * from collaboration_space_key_publications order by device_id"
                ),
        )

    fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    fun user(account: UUID) =
        "@${collaborationSpaceMatrixLocalpart(s.space,account)}:matrix.example.test"

    private fun compact(id: UUID) = id.toString().replace("-", "")

    fun intent(firstUser: String = user(pair[0])) =
        s.runtime.update(
            "insert into collaboration_space_room_intents(space_id,conversation_id,first_account_id,second_account_id,requested_by,matrix_creator_account_id,matrix_server_name,first_matrix_user_id,second_matrix_user_id,origin,created_at) values(?,?,?,?,?,?,'matrix.example.test',?,?,'SPACE',?::timestamptz)",
            s.space,
            conversation,
            pair[0],
            pair[1],
            s.account,
            s.account,
            firstUser,
            user(pair[1]),
            s.at.toString(),
        )

    fun enqueue(
        account: UUID = s.account,
        deviceVersion: Long = 1,
        generation: Long = 0,
        nativeVersion: Long = 0,
        lifetimeSeconds: Long = 120,
    ): Int {
        val at = now()
        return s.runtime.update(
            "insert into collaboration_space_room_provisioning(space_id,conversation_id,device_id,device_version,provider_generation,account_id,native_session_id,revision,native_session_version,credential_version,authenticated_at,correlation_id,client_platform,client_build,created_at,requested_at,expires_at,available_at,state,attempts) values(?,?,?,?,?,?,?,0,?,0,?::timestamptz,?,'ANDROID',28,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz,'QUEUED',0)",
            s.space,
            conversation,
            if (account == s.account) s.device else peerDevice,
            deviceVersion,
            generation,
            account,
            if (account == s.account) s.parent else peerParent,
            nativeVersion,
            s.actor.authenticatedAt.toString(),
            UUID.randomUUID(),
            at.toString(),
            at.toString(),
            at.plusSeconds(lifetimeSeconds).toString(),
            at.toString(),
        )
    }

    fun rows(table: String) = s.runtime.queryForList("select * from $table")

    fun <T> worker(actor: Actor = s.actor.copy(accountId = UUID(0, 0)), body: () -> T): Result<T> =
        workerTransactions.run(actor) { safeDatabaseCall(body) }

    fun claim(seconds: Int = 30) =
        worker.queryForList(
            "select * from claim_collaboration_space_room_provisioning(?,1,?)",
            UUID.randomUUID(),
            seconds,
        )

    fun publish(lease: Map<String, Any?>, creator: UUID = s.account, old: Boolean = false): Int {
        val columns = if (old) "" else ",matrix_creator_account_id"
        val values = if (old) "" else ",?"
        val args =
            mutableListOf<Any?>(
                s.space,
                conversation,
                lease["account_id"],
                lease["revision"],
                lease["lease_token"],
                boundRoom,
                now().toString(),
            )
        if (!old) args.add(creator)
        return worker.update(
            "insert into collaboration_space_room_bindings(space_id,conversation_id,published_by,request_revision,lease_token,matrix_room_id,created_at$columns) values(?,?,?,?,?,?,?::timestamptz$values)",
            *args.toTypedArray(),
        )
    }

    fun finish(lease: Map<String, Any?>) =
        worker.update(
            "update collaboration_space_room_provisioning set state='SUCCEEDED',completed_at=statement_timestamp(),last_outcome_revision=revision,last_outcome_state='SUCCEEDED',last_outcome_at=statement_timestamp(),failure_code=null,last_failure_code=null,lease_owner=null,lease_token=null,lease_until=null where space_id=? and conversation_id=? and lease_token=?",
            s.space,
            conversation,
            lease["lease_token"],
        )
}

internal class SpaceKeyProvider(val f: SpaceKeyExchangeFixture) :
    CollaborationMatrixKeyRepository by DisabledCollaborationMatrixKeyRepository() {
    val queries = AtomicInteger()
    val claims = AtomicInteger()
    val materials = linkedMapOf(f.ownKeys.userId to f.ownKeys, f.peerKeys.userId to f.peerKeys)
    val ownReads = AtomicInteger()
    var onOwn: () -> Unit = {}
    var onQuery: () -> Unit = {}
    var onClaim: () -> Unit = {}
    var queryFailure: Failure? = null
    var claimFailure: Failure? = null
    val received = mutableListOf<CollaborationMatrixKeyQuery>()

    private fun validate(token: String, deadline: Instant) {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
        assertEquals("fixture-owned-query-token", token)
        assertTrue(f.leases() in 1..7)
        assertTrue(deadline <= Instant.now().plusSeconds(30))
    }

    override fun ownDeviceKeys(
        userId: String,
        deviceId: String,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixDeviceKeys?> {
        validate(token, deadline)
        ownReads.incrementAndGet()
        assertEquals(f.ownKeys.userId, userId)
        assertEquals(f.ownKeys.deviceId, deviceId)
        val identity = materials[userId]?.device
        onOwn()
        return Result.Success(identity)
    }

    override fun deviceKeys(
        request: CollaborationMatrixKeyQuery,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixKeyQueryResult> {
        validate(token, deadline)
        queries.incrementAndGet()
        received += request
        val result =
            CollaborationMatrixKeyQueryResult(
                request.devices.mapValues { (user, ids) ->
                    materials[user]?.let {
                        if (it.deviceId in ids) mapOf(it.deviceId to it.device) else emptyMap()
                    } ?: emptyMap()
                }
            )
        onQuery()
        return queryFailure?.let { Result.Failed(it) } ?: Result.Success(result)
    }

    override fun claimKeys(
        request: CollaborationMatrixKeyClaim,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixKeyClaimResult> {
        validate(token, deadline)
        claims.incrementAndGet()
        val result =
            CollaborationMatrixKeyClaimResult(
                request.oneTimeKeys.mapValues { (user, ids) ->
                    ids.mapValues {
                        mapOf("signed_curve25519:fixture" to materials.getValue(user).signed())
                    }
                }
            )
        onClaim()
        return claimFailure?.let { Result.Failed(it) } ?: Result.Success(result)
    }
}

private fun <T> success(result: Result<T>): T {
    assertInstanceOf(Result.Success::class.java, result)
    return (result as Result.Success).value
}
