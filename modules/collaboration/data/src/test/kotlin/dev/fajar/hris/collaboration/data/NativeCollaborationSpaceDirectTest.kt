package dev.fajar.hris.collaboration.data

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.util.UUID
import java.util.concurrent.CancellationException
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceDirectTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun openAndLostResponseReplayPreserveIntentWithoutSelectingPeerIdentity() {
        val f = Fixture()
        val operation = UUID.randomUUID()
        val first = success(f.open(operation))
        assertEquals(0L, first.version)
        assertEquals(first.copy(replayed = true), success(f.open(operation)))
        assertEquals(first, success(f.open(UUID.randomUUID())))
        assertEquals(
            mapOf(
                "collaboration_space_direct_conversations" to 1,
                "operation_receipts" to 2,
                "audit_entries" to 1,
                "outbox_events" to 1,
            ),
            f.counts(),
        )
        val value = success(f.read().get(f.s.actor, f.s.parent, 0, f.s.space, f.f.client, first.id))
        assertEquals(f.s.account, value.createdBy)
        assertEquals(setOf(f.s.account, f.peer), setOf(value.firstAccountId, value.secondAccountId))
        assertEquals(
            listOf(value),
            success(f.read().list(f.s.actor, f.s.parent, 0, f.s.space, f.f.client, null, 50)).items,
        )
        assertNull(success(f.s.tx { f.repo.member(f.s.space, f.peer) })!!.identity)
        assertEquals(
            0,
            f.s.owner.queryForObject(
                "select count(*) from collaboration_space_identities where account_id=?",
                Int::class.java,
                f.peer,
            ),
        )
        f.revokePeer()
        failure(f.open(operation), "collaboration_peer_unavailable")
        assertEquals(
            value,
            success(f.read().get(f.s.actor, f.s.parent, 0, f.s.space, f.f.client, first.id)),
        )
        assertEquals(1, f.counts().getValue("collaboration_space_direct_conversations"))
    }

    @Test
    fun journalFailureAndLateAdmissionRollBackIntentReceiptAuditAndOutbox() {
        val f = Fixture()
        val originalTime = f.f.clock.at
        for (mode in listOf("failure", "cancel", "interrupt", "nativeExpiry", "peerRevoked")) {
            val journal =
                object : ChangeJournalRepository by f.f.journal {
                    override fun record(actor: Actor, change: ChangeRecord): Result<Unit> {
                        val result = f.f.journal.record(actor, change)
                        when (mode) {
                            "failure" ->
                                return Result.Failed(
                                    Failure(FailureKind.UNAVAILABLE, "fixture_journal_failure")
                                )
                            "cancel" -> throw CancellationException("fixture journal")
                            "interrupt" -> throw InterruptedException("fixture journal")
                            "nativeExpiry" -> f.f.clock.at = f.s.at.plusSeconds(901)
                            "peerRevoked" -> f.revokePeer()
                        }
                        return result
                    }
                }
            val call = { f.open(journal = journal) }
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { call() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { call() }
                else -> assertInstanceOf(Result.Failed::class.java, call(), mode)
            }
            assertTrue(f.counts().values.all { it == 0 }, mode)
            f.f.clock.at = originalTime
            if (mode == "peerRevoked") f.grantPeer()
        }
        success(f.open())
        assertTrue(f.counts().values.all { it == 1 })
    }

    @Test
    fun finalMemberAcquisitionCannotOutliveNativeAuthorityOrPublishAnUncertainReceipt() {
        val f = Fixture()
        var calls = 0
        val repo =
            object : CollaborationSpaceConversationRepository by f.repo {
                override fun member(
                    spaceId: UUID,
                    accountId: UUID,
                ): Result<CollaborationSpaceMember?> {
                    val result = f.repo.member(spaceId, accountId)
                    calls++
                    if (calls == 4) f.f.clock.at = f.s.at.plusSeconds(901)
                    return result
                }
            }
        assertInstanceOf(Result.Failed::class.java, f.open(repo = repo))
        assertEquals(4, calls)
        assertTrue(f.counts().values.all { it == 0 })
    }

    @Test
    fun nativeReadRechecksBothAcquisitionOutcomesAndKeepsOtherParticipantsPrivate() {
        val f = Fixture()
        val receipt = success(f.open())
        val originalTime = f.f.clock.at
        for (failed in listOf(false, true)) {
            val repo =
                object : CollaborationSpaceConversationRepository by f.repo {
                    override fun list(
                        spaceId: UUID,
                        accountId: UUID,
                        after: UUID?,
                        limit: Int,
                    ): Result<Page<CollaborationSpaceDirectConversation>> {
                        val result = f.repo.list(spaceId, accountId, after, limit)
                        f.f.clock.at = f.s.at.plusSeconds(901)
                        return if (failed)
                            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_acquisition"))
                        else result
                    }
                }
            failure(
                f.read(repo).list(f.s.actor, f.s.parent, 0, f.s.space, f.f.client, null, 50),
                "native_session_invalid",
            )
            f.f.clock.at = originalTime
        }
        failure(
            f.read().get(f.s.actor, f.s.parent, 0, f.s.space, f.f.client, UUID.randomUUID()),
            "collaboration_conversation_not_found",
        )
        failure(
            f.read()
                .get(
                    f.s.actor.copy(accountId = f.peer),
                    f.s.parent,
                    0,
                    f.s.space,
                    f.f.client,
                    receipt.id,
                ),
            "native_session_invalid",
        )
        f.s.owner.update(
            "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
            f.s.account,
        )
        failure(
            f.read().get(f.s.actor, f.s.parent, 0, f.s.space, f.f.client, receipt.id),
            "collaboration_access_denied",
        )
        assertEquals(1, f.counts().getValue("collaboration_space_direct_conversations"))
    }

    @Test
    fun invalidPeerClientScopeAndCurrentSecurityCannotOpenOrReplayAnIntent() {
        val f = Fixture()
        failure(f.open(peer = f.s.account), "invalid_collaboration_peer")
        failure(f.open(peer = UUID(0, 0)), "invalid_collaboration_peer")
        failure(f.open(peer = UUID.randomUUID()), "collaboration_peer_unavailable")
        failure(f.open(actor = f.s.actor.copy(companyId = f.s.company)), "platform_scope_required")
        failure(f.open(client = ClientVersion(ClientPlatform.ANDROID, 4)), "client_update_required")
        failure(f.open(client = ClientVersion(ClientPlatform.WEB, 5)), "invalid_client_version")
        failure(f.open(client = null), "invalid_client_version")
        failure(f.open(policy = CollaborationSpacePolicy(false)), "collaboration_unavailable")
        failure(f.open(version = 1), "native_session_invalid")
        failure(
            f.read().list(f.s.actor, f.s.parent, 0, f.s.space, f.f.client, null, 51),
            "invalid_collaboration_conversation_query",
        )
        assertTrue(f.counts().values.all { it == 0 })
        val operation = UUID.randomUUID()
        success(f.open(operation))
        val before = f.counts()
        f.s.owner.update(
            "update accounts set active=false,version=version+1 where id=?",
            f.s.account,
        )
        assertInstanceOf(Result.Failed::class.java, f.open(operation))
        assertEquals(before, f.counts())
    }

    @Test
    fun originalPhysicalReadUsesExplicitCompanyAndConfiguredServerWithoutCreatingIntent() {
        val f = Fixture()
        val before = f.counts()
        val value =
            f.original.copy(
                room = f.original.room!!.copy(roomId = "!original:untrusted-locator.test")
            )
        var calls = 0
        val repo =
            object : CollaborationSpaceConversationRepository by f.repo {
                override fun legacyBoundRoom(
                    spaceId: UUID,
                    accountId: UUID,
                    companyId: UUID,
                    serverName: String,
                    roomId: String,
                ): Result<CollaborationSpaceLegacyConversation?> {
                    calls++
                    assertEquals(
                        listOf(f.s.space, f.s.account, f.s.company),
                        listOf(spaceId, accountId, companyId),
                    )
                    assertEquals("matrix.example.test", serverName)
                    assertEquals(value.room!!.roomId, roomId)
                    return Result.Success(value)
                }
            }
        assertEquals(
            value,
            success(
                f.read(repo)
                    .legacyRoom(
                        f.s.actor,
                        f.s.parent,
                        0,
                        f.s.space,
                        f.f.client,
                        f.s.company,
                        value.room!!.roomId,
                    )
            ),
        )
        assertEquals(1, calls)
        assertEquals(before, f.counts())
    }

    @Test
    fun originalPhysicalReadRejectsMismatchedMetadataWithoutInventingASpaceBinding() {
        val f = Fixture()
        var value = f.original
        val repo =
            object : CollaborationSpaceConversationRepository by f.repo {
                override fun legacyBoundRoom(
                    spaceId: UUID,
                    accountId: UUID,
                    companyId: UUID,
                    serverName: String,
                    roomId: String,
                ): Result<CollaborationSpaceLegacyConversation?> = Result.Success(value)
            }
        val original = f.original
        for (changed in
            listOf(
                original.copy(spaceId = UUID.randomUUID()),
                original.copy(conversation = original.conversation.copy(companyId = f.other)),
                original.copy(
                    conversation =
                        original.conversation.copy(
                            firstAccountId = UUID.randomUUID(),
                            secondAccountId = UUID.randomUUID(),
                        )
                ),
                original.copy(room = null),
                original.copy(room = original.room!!.copy(serverName = "other.test")),
                original.copy(room = original.room!!.copy(roomId = "!other:matrix.example.test")),
            )) {
            value = changed
            failure(
                f.read(repo)
                    .legacyRoom(
                        f.s.actor,
                        f.s.parent,
                        0,
                        f.s.space,
                        f.f.client,
                        f.s.company,
                        "!original:matrix.example.test",
                    ),
                "collaboration_original_room_changed",
            )
        }
        assertTrue(f.counts().values.all { it == 0 })
    }

    @Test
    fun currentNativeDenialWinsOverMissingFailedAndSuccessfulPhysicalLookup() {
        val f = Fixture()
        val start = f.f.clock.at
        for (mode in 0..2) {
            f.f.clock.at = start
            val repo =
                object : CollaborationSpaceConversationRepository by f.repo {
                    override fun legacyBoundRoom(
                        spaceId: UUID,
                        accountId: UUID,
                        companyId: UUID,
                        serverName: String,
                        roomId: String,
                    ): Result<CollaborationSpaceLegacyConversation?> {
                        f.f.clock.at = start.plusSeconds(901)
                        return when (mode) {
                            0 -> Result.Success(null)
                            1 -> Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_storage"))
                            else -> Result.Success(f.original)
                        }
                    }
                }
            failure(
                f.read(repo)
                    .legacyRoom(
                        f.s.actor,
                        f.s.parent,
                        0,
                        f.s.space,
                        f.f.client,
                        f.s.company,
                        "!original:matrix.example.test",
                    ),
                "native_session_invalid",
            )
        }
    }

    @Test
    fun physicalLookupHasThirtySecondBudgetAndRejectsBackwardClockWithoutRetry() {
        val f = Fixture()
        val start = f.f.clock.at.plusSeconds(10)
        var offset = 0L
        var calls = 0
        val repo =
            object : CollaborationSpaceConversationRepository by f.repo {
                override fun legacyBoundRoom(
                    spaceId: UUID,
                    accountId: UUID,
                    companyId: UUID,
                    serverName: String,
                    roomId: String,
                ): Result<CollaborationSpaceLegacyConversation?> {
                    calls++
                    f.f.clock.at = start.plusSeconds(offset)
                    return Result.Success(f.original)
                }
            }
        for (seconds in listOf(-1L, 30L, 31L, 29L)) {
            f.f.clock.at = start
            offset = seconds
            val result =
                f.read(repo)
                    .legacyRoom(
                        f.s.actor,
                        f.s.parent,
                        0,
                        f.s.space,
                        f.f.client,
                        f.s.company,
                        "!original:matrix.example.test",
                    )
            if (seconds == 29L) assertEquals(f.original, success(result))
            else failure(result, "collaboration_room_read_expired")
        }
        assertEquals(4, calls)
    }

    @Test
    fun deniedActorCannotUseMissingOriginalAsAnAdmissionShortcut() {
        val f = Fixture()
        f.s.owner.update(
            "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
            f.s.account,
        )
        var called = false
        val repo =
            object : CollaborationSpaceConversationRepository by f.repo {
                override fun legacyBoundRoom(
                    spaceId: UUID,
                    accountId: UUID,
                    companyId: UUID,
                    serverName: String,
                    roomId: String,
                ): Result<CollaborationSpaceLegacyConversation?> {
                    called = true
                    return Result.Success(null)
                }
            }
        failure(
            f.read(repo)
                .legacyRoom(
                    f.s.actor,
                    f.s.parent,
                    0,
                    f.s.space,
                    f.f.client,
                    f.s.company,
                    "!unknown:matrix.example.test",
                ),
            "collaboration_access_denied",
        )
        assertFalse(called)
    }

    @Test
    fun originalPhysicalLookupPropagatesCancellationAndInterruption() {
        val f = Fixture()
        for (interrupted in listOf(false, true)) {
            var calls = 0
            val repo =
                object : CollaborationSpaceConversationRepository by f.repo {
                    override fun legacyBoundRoom(
                        spaceId: UUID,
                        accountId: UUID,
                        companyId: UUID,
                        serverName: String,
                        roomId: String,
                    ): Result<CollaborationSpaceLegacyConversation?> {
                        calls++
                        if (interrupted) throw InterruptedException("fixture")
                        else throw CancellationException("fixture")
                    }
                }
            val call = {
                f.read(repo)
                    .legacyRoom(
                        f.s.actor,
                        f.s.parent,
                        0,
                        f.s.space,
                        f.f.client,
                        f.s.company,
                        "!original:matrix.example.test",
                    )
            }
            if (interrupted) assertThrows(InterruptedException::class.java) { call() }
            else assertThrows(CancellationException::class.java) { call() }
            assertEquals(1, calls)
        }
        assertTrue(f.counts().values.all { it == 0 })
    }

    @Test
    fun malformedPhysicalQueriesAndDisabledProviderDoNotReadOrCreateRooms() {
        val f = Fixture()
        for ((company, room) in
            listOf(
                UUID(0, 0) to "!original:test",
                f.s.company to "bad",
                f.s.company to "!${"x".repeat(255)}",
            )) failure(
            f.read().legacyRoom(f.s.actor, f.s.parent, 0, f.s.space, f.f.client, company, room),
            "invalid_collaboration_conversation_query",
        )
        for (provider in
            listOf(
                CollaborationMatrixPolicy(false, "matrix.example.test"),
                CollaborationMatrixPolicy(true, "bad/server"),
            )) failure(
            f.read(provider = provider)
                .legacyRoom(
                    f.s.actor,
                    f.s.parent,
                    0,
                    f.s.space,
                    f.f.client,
                    f.s.company,
                    "!original:matrix.example.test",
                ),
            "collaboration_unavailable",
        )
        assertTrue(f.counts().values.all { it == 0 })
    }

    @Test
    fun wrongParentAndCompanyActorCannotAcquireOriginalPhysicalMetadata() {
        val f = Fixture()
        failure(
            f.read()
                .legacyRoom(
                    f.s.actor.copy(companyId = f.s.company),
                    f.s.parent,
                    0,
                    f.s.space,
                    f.f.client,
                    f.s.company,
                    "!original:matrix.example.test",
                ),
            "platform_scope_required",
        )
        failure(
            f.read()
                .legacyRoom(
                    f.s.actor,
                    UUID.randomUUID(),
                    0,
                    f.s.space,
                    f.f.client,
                    f.s.company,
                    "!original:matrix.example.test",
                ),
            "native_session_invalid",
        )
        failure(
            f.read()
                .legacyRoom(
                    f.s.actor,
                    f.s.parent,
                    1,
                    f.s.space,
                    f.f.client,
                    f.s.company,
                    "!original:matrix.example.test",
                ),
            "native_session_invalid",
        )
    }

    private inner class Fixture {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val s = f.storage
        val peer = UUID.randomUUID()
        val other = UUID.randomUUID()
        val original =
            CollaborationSpaceLegacyConversation(
                s.space,
                CollaborationDirectConversation(
                    UUID.randomUUID(),
                    s.company,
                    listOf(s.account, peer).sortedBy(UUID::toString)[0],
                    listOf(s.account, peer).sortedBy(UUID::toString)[1],
                    s.account,
                    s.at,
                ),
                CollaborationLegacyRoomMetadata(
                    "matrix.example.test",
                    "!original:matrix.example.test",
                    s.at,
                    s.account,
                ),
            )

        init {
            Flyway.configure().dataSource(s.source).target("130").load().migrate()
            s.owner.execute(
                "grant select,insert,update,delete on collaboration_space_direct_conversations to space_gateway_fixture"
            )
            s.owner.execute(
                "grant select on collaboration_space_members,collaboration_space_legacy_conversations to space_gateway_fixture"
            )
            s.owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional peer company','UTC')",
                other,
                other.toString().replace("-", ""),
            )
            s.owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional peer')",
                peer,
                "$peer@example.test",
            )
            s.owner.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                other,
                peer,
            )
            grantPeer()
        }

        val repo =
            StoredCollaborationSpaceConversationRepository(
                PostgresCollaborationSpaceConversationDataSource(f.sql)
            )

        fun open(
            operation: UUID = UUID.randomUUID(),
            peer: UUID = this.peer,
            actor: Actor = s.actor,
            version: Long = 0,
            client: ClientVersion? = f.client,
            repo: CollaborationSpaceConversationRepository = this.repo,
            journal: ChangeJournalRepository = f.journal,
            policy: CollaborationSpacePolicy = CollaborationSpacePolicy(true, 5, 5),
        ): Result<MutationReceipt> =
            OpenNativeCollaborationSpaceDirect(
                    repo,
                    f.spaces,
                    f.sessions,
                    f.identities,
                    f.operations,
                    journal,
                    s.transactions,
                    policy,
                    IdentitySecurityPolicy(),
                    f.clock,
                )
                .execute(actor, s.parent, version, s.space, client, operation, peer)

        fun read(
            repo: CollaborationSpaceConversationRepository = this.repo,
            provider: CollaborationMatrixPolicy =
                CollaborationMatrixPolicy(true, "matrix.example.test"),
        ) =
            ReadNativeCollaborationSpaceDirect(
                repo,
                f.spaces,
                f.sessions,
                f.identities,
                s.transactions,
                CollaborationSpacePolicy(true, 5, 5),
                IdentitySecurityPolicy(),
                f.clock,
                provider,
            )

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
                    "collaboration_space_direct_conversations",
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                )
                .associateWith {
                    s.owner.queryForObject("select count(*) from $it", Int::class.java)!!
                }
    }

    private fun <T> success(result: Result<T>): T = (result as Result.Success).value

    private fun failure(result: Result<*>, code: String) {
        assertInstanceOf(Result.Failed::class.java, result)
        assertEquals(code, (result as Result.Failed).failure.code)
    }
}
