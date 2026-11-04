package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationDirectRoomPower
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceRoomWorkerTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun claimedRoomPublishesAtomicBindingWithOwnedEncryptedCredentialsAndReleasesItsSlot() {
        val f = Fixture()
        val lease = f.claim()
        val before = f.counts()
        success(f.advance().execute(lease))
        val binding = requireNotNull(f.binding())
        assertEquals(f.matrix.id, binding.roomId)
        assertEquals(f.s.account, binding.publishedBy)
        assertEquals(f.s.account, binding.matrixCreatorAccountId)
        assertEquals(lease.token, binding.leaseToken)
        assertEquals(CollaborationSpaceProvisioningState.SUCCEEDED, f.head().state)
        assertEquals(before.getValue("audit_entries") + 1, f.counts().getValue("audit_entries"))
        assertEquals(before.getValue("outbox_events") + 1, f.counts().getValue("outbox_events"))
        assertEquals(0, f.slotCount())
        assertEquals(listOf("resolve", "create", "resolve", "join", "observe"), f.matrix.calls)
        val calls = f.matrix.calls.toList()
        assertInstanceOf(Result.Failed::class.java, f.advance().execute(lease))
        assertEquals(calls, f.matrix.calls)
        assertEquals(binding, f.binding())
    }

    @Test
    fun originalCanonicalPeerCreatorIsRetainedWhenTheCurrentRequesterRecoversItsAlias() {
        val f = Fixture()
        val lease = f.claim()
        f.matrix.created = true
        f.matrix.creator = f.peer
        success(f.advance().execute(lease))
        assertEquals(f.peer, requireNotNull(f.binding()).matrixCreatorAccountId)
        assertEquals(f.s.account, requireNotNull(f.binding()).publishedBy)
        assertFalse(f.matrix.calls.contains("create"))
        assertEquals(listOf("resolve", "join", "observe"), f.matrix.calls)
        assertEquals(0, f.slotCount())
    }

    @Test
    fun lostCreationResponseStillPublishesOnlyTheSameStableAlias() {
        val f = Fixture()
        val lease = f.claim()
        f.matrix.lostCreation = true
        success(f.advance().execute(lease))
        assertEquals(f.matrix.id, requireNotNull(f.binding()).roomId)
        assertEquals(1, f.matrix.calls.count { it == "create" })
        assertEquals(2, f.matrix.calls.count { it == "resolve" })
        assertEquals(0, f.slotCount())
    }

    @Test
    fun peerRevocationAfterSuccessfulOrFailedProviderResponsePreventsPublication() {
        val f = Fixture()
        val lease = f.claim()
        for (failed in listOf(false, true)) {
            f.matrix.failObservation = failed
            f.matrix.onCall = { if (it == "observe") f.revokePeer() }
            val result = f.advance().execute(lease)
            assertInstanceOf(Result.Failed::class.java, result)
            assertEquals("collaboration_peer_unavailable", (result as Result.Failed).failure.code)
            assertNull(f.binding())
            assertEquals(CollaborationSpaceProvisioningState.RUNNING, f.head().state)
            assertEquals(0, f.slotCount())
            f.grantPeer()
        }
    }

    @Test
    fun journalFailureAndProviderCancellationRollbackPublicationAndReleaseOnlyTheirOwnSlot() {
        val f = Fixture()
        val lease = f.claim()
        val before = f.counts()
        val journal =
            object : ChangeJournalRepository by f.f.journal {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    f.f.journal.record(actor, change).flatMap {
                        Result.Failed(Failure(FailureKind.CONFLICT, "fixture_journal_rejected"))
                    }
            }
        assertInstanceOf(Result.Failed::class.java, f.advance(journal).execute(lease))
        assertNull(f.binding())
        assertEquals(CollaborationSpaceProvisioningState.RUNNING, f.head().state)
        assertEquals(before, f.counts())
        assertEquals(0, f.slotCount())
        f.matrix.onCall = {
            if (it == "observe") throw CancellationException("fixture room cancelled")
        }
        assertThrows(CancellationException::class.java) { f.advance().execute(lease) }
        assertNull(f.binding())
        assertEquals(0, f.slotCount())
    }

    @Test
    fun providerRotationAfterTheExternalOutcomeInvalidatesTheCapturedQueueProof() {
        val f = Fixture()
        val lease = f.claim()
        var rotated = false
        f.matrix.onCall = {
            if (!rotated) {
                f.rotateProvider()
                rotated = true
            }
        }
        assertInstanceOf(Result.Failed::class.java, f.advance().execute(lease))
        assertEquals(listOf("resolve"), f.matrix.calls)
        assertNull(f.binding())
        assertEquals(0, f.slotCount())
        assertTrue(rotated)
    }

    @Test
    fun occupiedSlotsPreventProviderIoAndSettleSchedulesOnlyTheExactFiniteAttempt() {
        val f = Fixture()
        val lease = f.claim()
        val position =
            CollaborationSpaceRequestPosition(
                CollaborationSpaceRequestOwner(f.s.space, f.s.account, f.s.device, f.s.parent),
                CollaborationSpaceRequestKind.ROOM_PREPARATION,
            )
        val held =
            (1..7).map {
                requireNotNull(
                    success(
                        f.s.tx {
                            f.slots.claim(
                                position,
                                Instant.now().plusSeconds(30).truncatedTo(ChronoUnit.MICROS),
                            )
                        }
                    )
                )
            }
        val result = f.advance().execute(lease)
        assertInstanceOf(Result.Failed::class.java, result)
        assertEquals("collaboration_space_request_busy", (result as Result.Failed).failure.code)
        assertTrue(f.matrix.calls.isEmpty())
        assertTrue(success(f.settle().execute(lease, result.failure)))
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, f.head().state)
        assertFalse(success(f.settle().execute(lease, result.failure)))
        assertEquals(7, f.slotCount())
        held.forEach { assertTrue(success(f.s.tx { f.slots.release(it) })) }
    }

    @Test
    fun createdRoomMustResolveToItsExactImmutableAliasBeforeJoinOrPublication() {
        val f = Fixture()
        val lease = f.claim()
        f.matrix.aliasRoom = "!other:matrix.example.test"
        val result = f.advance().execute(lease)
        assertEquals("collaboration_room_mismatch", (result as Result.Failed).failure.code)
        assertEquals(listOf("resolve", "create", "resolve"), f.matrix.calls)
        assertNull(f.binding())
        assertEquals(0, f.slotCount())
    }

    @Test
    fun foreignOrConflictingObservedStateNeverBecomesABinding() {
        val f = Fixture()
        val lease = f.claim()
        f.matrix.created = true
        val invalid:
            List<
                (CollaborationSpaceMatrixRoomObservation) -> CollaborationSpaceMatrixRoomObservation
            > =
            listOf(
                { it.copy(members = it.members + ("@foreign:matrix.example.test" to "join")) },
                { it.copy(roomVersion = "11") },
                { it.copy(marker = null) },
                { it.copy(encryptionAlgorithm = "unsupported") },
            )
        for (change in invalid) {
            f.matrix.rewrite = change
            assertEquals(
                "collaboration_room_mismatch",
                (f.advance().execute(lease) as Result.Failed).failure.code,
            )
            assertNull(f.binding())
            assertEquals(0, f.slotCount())
        }
    }

    @Test
    fun recoveringPeerCannotRepairMissingCreatorStateAndFailureSettlesWithoutImpersonation() {
        val f = Fixture()
        val lease = f.claim()
        f.matrix.created = true
        f.matrix.creator = f.peer
        f.matrix.missing += CollaborationMatrixRoomSetting.ENCRYPTION
        val result = f.advance().execute(lease)
        assertEquals(
            "collaboration_room_recovery_requires_creator",
            (result as Result.Failed).failure.code,
        )
        assertFalse(f.matrix.calls.any { it.startsWith("put-") || it == "invite" })
        assertTrue(success(f.settle().execute(lease, result.failure)))
        assertEquals(CollaborationSpaceRoomFailure.ROOM_MISMATCH, f.head().lastOutcome?.failure)
        assertNull(f.binding())
        assertEquals(0, f.slotCount())
    }

    @Test
    fun actualCreatorRepairsOnlyMissingSettingsAndInviteThenVerifiesBeforePublication() {
        val f = Fixture()
        val lease = f.claim()
        f.matrix.created = true
        f.matrix.missing +=
            setOf(
                CollaborationMatrixRoomSetting.ENCRYPTION,
                CollaborationMatrixRoomSetting.GUEST_ACCESS,
            )
        f.matrix.invited = false
        success(f.advance().execute(lease))
        assertEquals(f.s.account, requireNotNull(f.binding()).matrixCreatorAccountId)
        assertTrue(f.matrix.missing.isEmpty())
        assertTrue(f.matrix.invited)
        assertEquals(2, f.matrix.calls.count { it == "observe" })
        assertEquals(
            setOf("put-ENCRYPTION", "put-GUEST_ACCESS"),
            f.matrix.calls.filter { it.startsWith("put-") }.toSet(),
        )
        assertEquals(0, f.slotCount())
    }

    @Test
    fun actualSynapsePeerCreatedRoomPublishesUnderCurrentOwnProofAndKeepsKeysAfterRestart() {
        MatrixProviderFixture("10m").use { provider ->
            provider.start()
            val f = Fixture()
            fun raw() = HttpMatrixClientDataSource(provider.client, provider.config, provider.clock)
            fun deadline() = Instant.now().plusSeconds(20)
            val group =
                InternalCollaborationSpaceMatrixRepository(
                    HmacMatrixJwtDataSource(provider.config),
                    raw(),
                    provider.clock,
                )
            fun login(account: UUID, device: UUID): CollaborationMatrixCredential {
                val at = Instant.now()
                return success(
                    group.login(
                        CollaborationSpaceMatrixLogin(
                            f.s.space,
                            account,
                            device,
                            null,
                            null,
                            at,
                            at.plusSeconds(60),
                        )
                    )
                )
            }
            val own = login(f.s.account, f.s.device)
            val peer = login(f.peer, UUID.randomUUID())
            assertEquals(f.s.user, own.userId)
            f.rotateProvider(own.accessToken, own.expiresAt)
            val ownKeys = MatrixKeyFixture(own.userId, own.deviceId)
            val peerKeys = MatrixKeyFixture(peer.userId, peer.deviceId)
            val keys = InternalCollaborationMatrixKeyRepository(raw())
            success(
                keys.upload(
                    CollaborationMatrixKeyUpload(ownKeys.device),
                    own.accessToken,
                    deadline(),
                )
            )
            success(
                keys.upload(
                    CollaborationMatrixKeyUpload(peerKeys.device),
                    peer.accessToken,
                    deadline(),
                )
            )
            val rooms =
                InternalCollaborationSpaceMatrixRoomRepository(raw(), provider.config.serverName)
            val original =
                CollaborationSpaceMatrixDirectRoom(
                    f.s.space,
                    f.conversation,
                    f.peer,
                    f.s.account,
                    peer.userId,
                    own.userId,
                )
            val created =
                success(rooms.create(original, peer.accessToken, deadline()))
                    as CollaborationMatrixRoomCreation.Created
            val lease = f.claim()
            success(f.advance(protocol = rooms).execute(lease))
            val binding = requireNotNull(f.binding())
            assertEquals(created.roomId, binding.roomId)
            assertEquals(f.peer, binding.matrixCreatorAccountId)
            assertEquals(f.s.account, binding.publishedBy)
            assertEquals(CollaborationSpaceProvisioningState.SUCCEEDED, f.head().state)
            assertEquals(0, f.slotCount())
            val query =
                CollaborationMatrixKeyQuery(
                    mapOf(own.userId to listOf(own.deviceId), peer.userId to listOf(peer.deviceId))
                )
            val expectedKeys =
                mapOf(
                    own.userId to mapOf(own.deviceId to ownKeys.device),
                    peer.userId to mapOf(peer.deviceId to peerKeys.device),
                )
            assertEquals(
                expectedKeys,
                success(keys.deviceKeys(query, own.accessToken, deadline())).deviceKeys,
            )
            provider.restart()
            val restarted = raw()
            val same =
                InternalCollaborationSpaceMatrixRoomRepository(
                    restarted,
                    provider.config.serverName,
                )
            assertEquals(
                created.roomId,
                success(same.resolve(original, own.accessToken, deadline())),
            )
            assertEquals(
                peer.userId,
                success(same.observe(created.roomId, own.accessToken, deadline())).creator,
            )
            assertEquals(
                expectedKeys,
                success(
                        InternalCollaborationMatrixKeyRepository(restarted)
                            .deviceKeys(query, own.accessToken, deadline())
                    )
                    .deviceKeys,
            )
            assertEquals(binding, f.binding())
        }
    }

    private inner class Fixture {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val s = f.storage
        val peer = UUID.randomUUID()
        val other = UUID.randomUUID()
        val peerParent = UUID.randomUUID()
        val peerActor = s.actor.copy(accountId = peer)
        val conversation = UUID.randomUUID()
        val pair = listOf(s.account, peer).sortedBy(UUID::toString)

        init {
            Flyway.configure().dataSource(s.source).target("134").load().migrate()
            s.owner.execute(
                "grant select,insert,update,delete on all tables in schema public to space_gateway_fixture,space_gateway_worker"
            )
            // This fixture exercises worker capability. Ordinary account-only RLS has separate
            // tests.
            s.owner.execute("grant hris_worker_capability to space_gateway_fixture")
            s.owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional native room peer company','UTC')",
                other,
                other.toString().replace("-", ""),
            )
            s.owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional native room peer')",
                peer,
                "$peer@example.test",
            )
            s.owner.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                other,
                peer,
            )
            grantPeer()
            s.owner.update(
                "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,created_at) values(?,?,'matrix.example.test',?,'SPACE',?::timestamptz)",
                s.space,
                peer,
                "@hrisg_${s.space.toString().replace("-", "")}_${peer.toString().replace("-", "")}:matrix.example.test",
                s.at.toString(),
            )
            s.owner.update(
                "insert into collaboration_space_direct_conversations(space_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?::timestamptz)",
                s.space,
                conversation,
                pair[0],
                pair[1],
                peer,
                s.at.toString(),
            )
            s.owner.update(
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
                peerParent,
                peer,
                peerParent.toString().replace("-", "").repeat(2),
                peerActor.authenticatedAt.toString(),
                s.at.plusSeconds(3600).toString(),
                s.at.plusSeconds(900).toString(),
                s.at.minusSeconds(60).toString(),
                s.at.minusSeconds(30).toString(),
            )
            // Worker control deadlines must start from the current real time.
            f.clock.at = Instant.now().truncatedTo(ChronoUnit.MICROS)
        }

        val rooms =
            StoredCollaborationSpaceRoomRepository(PostgresCollaborationSpaceRoomDataSource(f.sql))
        val conversations =
            StoredCollaborationSpaceConversationRepository(
                PostgresCollaborationSpaceConversationDataSource(f.sql)
            )

        val slots =
            StoredCollaborationSpaceRequestRepository(
                PostgresCollaborationSpaceRequestDataSource(f.sql)
            )
        val providers =
            EncryptedCollaborationSpaceProviderSessionRepository(
                PostgresCollaborationSpaceProviderSessionDataSource(f.sql),
                JceIdentityTokenDataSource(s.currentKeys),
            )
        val token = "fixture-owned-room-token"
        val matrix = Matrix(this)

        init {
            rotateProvider()
        }

        fun rotateProvider(
            providerToken: String = token,
            expiresAt: Instant = Instant.now().plusSeconds(300),
        ) {
            success(
                s.tx {
                    providers.metadata(s.space, s.account, s.device).flatMap {
                        val before = requireNotNull(it)
                        providers.rotate(
                            CollaborationSpaceProviderSession(
                                before.copy(
                                    generation = before.generation + 1,
                                    updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
                                    expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS),
                                ),
                                providerToken,
                            ),
                            before.generation,
                        )
                    }
                }
            )
        }

        fun claim(): CollaborationSpaceRoomLease {
            f.clock.at = Instant.now().truncatedTo(ChronoUnit.MICROS)
            success(open())
            return success(
                    s.tx(s.actor.copy(accountId = UUID(0, 0))) {
                        ClaimCollaborationSpaceRooms(
                                rooms,
                                CollaborationSpacePolicy(true, 5, 5),
                                CollaborationMatrixPolicy(true, "matrix.example.test"),
                            )
                            .execute(UUID.randomUUID(), 1)
                    }
                )
                .single()
        }

        fun advance(
            journal: ChangeJournalRepository = f.journal,
            protocol: CollaborationSpaceMatrixRoomRepository = matrix,
        ) =
            AdvanceCollaborationSpaceRoom(
                rooms,
                conversations,
                f.registry,
                providers,
                f.selections,
                f.legacy,
                f.completions,
                f.provisioning,
                slots,
                protocol,
                DisabledCollaborationMatrixRoomRepository(),
                f.spaces,
                f.sessions,
                f.identities,
                journal,
                s.transactions,
                IdentitySecurityPolicy(),
                CollaborationSpacePolicy(true, 5, 5),
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                Clock.systemUTC(),
            )

        fun settle() =
            SettleCollaborationSpaceRoomFailure(
                rooms,
                f.identities,
                f.journal,
                s.transactions,
                Clock.systemUTC(),
            )

        fun binding() = success(s.tx { rooms.binding(s.space, s.account, conversation) })

        fun head() =
            requireNotNull(success(s.tx { rooms.provisioning(s.space, s.account, conversation) }))

        fun slotCount() =
            s.owner.queryForObject(
                "select count(*) from collaboration_space_request_leases",
                Int::class.java,
            )!!

        fun command(journal: ChangeJournalRepository = f.journal) =
            RequestNativeCollaborationSpaceRoom(
                rooms,
                conversations,
                f.registry,
                f.providers,
                f.selections,
                f.legacy,
                f.completions,
                f.provisioning,
                f.spaces,
                f.sessions,
                f.identities,
                f.operations,
                journal,
                s.transactions,
                IdentitySecurityPolicy(),
                CollaborationSpacePolicy(true, 5, 5),
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                f.clock,
            )

        fun open(
            operation: UUID = UUID.randomUUID(),
            revision: Long? = null,
            journal: ChangeJournalRepository = f.journal,
        ) =
            command(journal)
                .execute(
                    s.actor,
                    s.parent,
                    0,
                    s.space,
                    operation,
                    conversation,
                    s.device,
                    1,
                    revision,
                    f.client,
                    revision?.let { s.account },
                )

        fun read(
            actor: Actor = s.actor,
            parent: UUID = s.parent,
            rooms: CollaborationSpaceRoomRepository = this.rooms,
        ) =
            ReadNativeCollaborationSpaceRoom(
                    rooms,
                    conversations,
                    f.spaces,
                    f.sessions,
                    f.identities,
                    s.transactions,
                    CollaborationSpacePolicy(true, 5, 5),
                    CollaborationMatrixPolicy(true, "matrix.example.test"),
                    IdentitySecurityPolicy(),
                    f.clock,
                )
                .execute(actor, parent, 0, s.space, conversation, f.client)

        fun revokePeer() {
            s.owner.update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                peer,
            )
        }

        fun grantPeer() {
            s.owner.update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                other,
                peer,
            )
        }

        fun counts() =
            listOf(
                    "collaboration_space_room_intents",
                    "collaboration_space_room_provisioning",
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                )
                .associateWith {
                    s.owner.queryForObject("select count(*) from $it", Int::class.java)!!
                }
    }

    private class Matrix(private val f: Fixture) : CollaborationSpaceMatrixRoomRepository {
        val id = "!group-owned:matrix.example.test"
        val calls = mutableListOf<String>()
        var created = false
        var creator = f.s.account
        var joined = false
        var lostCreation = false
        var failObservation = false
        var onCall: (String) -> Unit = {}
        var aliasRoom: String? = null
        var invited = true
        val missing = mutableSetOf<CollaborationMatrixRoomSetting>()
        var rewrite:
            (CollaborationSpaceMatrixRoomObservation) -> CollaborationSpaceMatrixRoomObservation =
            {
                it
            }

        private fun entry(method: String, token: String, deadline: Instant) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), method)
            assertEquals(f.token, token)
            assertTrue(deadline > Instant.now())
            assertEquals(
                deadline,
                f.s.owner
                    .queryForObject(
                        "select expires_at from collaboration_space_request_leases where kind='ROOM_PREPARATION'",
                        java.sql.Timestamp::class.java,
                    )!!
                    .toInstant(),
            )
            calls += method
            onCall(method)
        }

        private fun coordinates(request: CollaborationSpaceMatrixDirectRoom) {
            assertEquals(f.s.space, request.spaceId)
            assertEquals(f.conversation, request.conversationId)
            assertEquals(f.s.account, request.creatorAccountId)
            assertEquals(f.peer, request.peerAccountId)
            assertEquals(f.s.user, request.creatorUserId)
        }

        override fun resolve(
            request: CollaborationSpaceMatrixDirectRoom,
            token: String,
            deadline: Instant,
        ): Result<String?> {
            coordinates(request)
            entry("resolve", token, deadline)
            return Result.Success(if (created) aliasRoom ?: id else null)
        }

        override fun create(
            request: CollaborationSpaceMatrixDirectRoom,
            token: String,
            deadline: Instant,
        ): Result<CollaborationMatrixRoomCreation> {
            coordinates(request)
            entry("create", token, deadline)
            if (created) return Result.Success(CollaborationMatrixRoomCreation.AliasInUse)
            created = true
            if (lostCreation)
                return Result.Failed(
                    Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable")
                )
            return Result.Success(CollaborationMatrixRoomCreation.Created(id))
        }

        override fun join(roomId: String, token: String, deadline: Instant): Result<Unit> {
            assertTrue(created)
            assertEquals(id, roomId)
            entry("join", token, deadline)
            joined = true
            return Result.Success(Unit)
        }

        override fun observe(
            roomId: String,
            token: String,
            deadline: Instant,
        ): Result<CollaborationSpaceMatrixRoomObservation> {
            assertEquals(id, roomId)
            entry("observe", token, deadline)
            if (failObservation)
                return Result.Failed(
                    Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable")
                )
            val peer =
                "@hrisg_${f.s.space.toString().replace("-", "")}_${f.peer.toString().replace("-", "")}:matrix.example.test"
            val state =
                CollaborationSpaceMatrixRoomObservation(
                    if (creator == f.s.account) f.s.user else peer,
                    "12",
                    false,
                    CollaborationSpaceMatrixRoomMarker(
                        f.s.space.toString(),
                        f.conversation.toString(),
                        "DIRECT",
                    ),
                    emptyList(),
                    false,
                    if (CollaborationMatrixRoomSetting.ENCRYPTION in missing) null
                    else "m.megolm.v1.aes-sha2",
                    "invite",
                    "invited",
                    if (CollaborationMatrixRoomSetting.GUEST_ACCESS in missing) null
                    else "forbidden",
                    collaborationDirectRoomPower(),
                    buildMap {
                        put(f.s.user, if (joined) "join" else "invite")
                        if (invited) put(peer, "join")
                    },
                )
            return Result.Success(rewrite(state))
        }

        override fun putInitialSetting(
            roomId: String,
            setting: CollaborationMatrixRoomSetting,
            token: String,
            deadline: Instant,
        ): Result<Unit> {
            assertEquals(id, roomId)
            assertEquals(f.s.account, creator)
            entry("put-${setting.name}", token, deadline)
            assertTrue(missing.remove(setting))
            return Result.Success(Unit)
        }

        override fun invitePeer(
            roomId: String,
            request: CollaborationSpaceMatrixDirectRoom,
            token: String,
            deadline: Instant,
        ): Result<Unit> {
            assertEquals(id, roomId)
            assertEquals(f.s.account, creator)
            coordinates(request)
            entry("invite", token, deadline)
            invited = true
            return Result.Success(Unit)
        }
    }

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, value)
        return (value as Result.Success).value
    }
}
