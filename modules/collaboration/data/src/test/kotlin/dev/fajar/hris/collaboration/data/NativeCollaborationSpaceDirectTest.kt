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
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

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

    private inner class Fixture {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val s = f.storage
        val peer = UUID.randomUUID()
        val other = UUID.randomUUID()

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

        fun read(repo: CollaborationSpaceConversationRepository = this.repo) =
            ReadNativeCollaborationSpaceDirect(
                repo,
                f.spaces,
                f.sessions,
                f.identities,
                s.transactions,
                CollaborationSpacePolicy(true, 5, 5),
                IdentitySecurityPolicy(),
                f.clock,
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
