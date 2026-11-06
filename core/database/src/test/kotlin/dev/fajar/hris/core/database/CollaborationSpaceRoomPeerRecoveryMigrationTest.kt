package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceRoomPeerRecoveryMigrationTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun upgradePreservesOriginalPublisherCreatorAndPrivateProofThroughRestore() {
        val f = Fixture("132")
        success(
            f.s.run {
                f.intent()
                f.enqueue()
            }
        )
        val lease = success(f.worker { f.claim() }).single()
        success(
            f.worker(f.s.actor) {
                f.publish(lease, old = true)
                f.finish(lease)
            }
        )
        val oldQueue = f.s.owner.queryForList("select * from collaboration_space_room_provisioning")
        val oldBinding =
            f.s.owner.queryForList("select * from collaboration_space_room_bindings").single()
        Flyway.configure().dataSource(f.s.source).target("133").load().migrate()
        assertEquals(
            oldQueue,
            f.s.owner.queryForList("select * from collaboration_space_room_provisioning"),
        )
        val expected = oldBinding + mapOf("matrix_creator_account_id" to f.s.account)
        assertEquals(
            expected,
            f.s.owner.queryForList("select * from collaboration_space_room_bindings").single(),
        )
        f.s.owner.update("delete from native_sessions where id in (?,?)", f.s.parent, f.peerParent)
        val restored = f.s.restore()
        assertEquals(
            expected,
            restored.queryForList("select * from collaboration_space_room_bindings").single(),
        )
        assertEquals(
            oldQueue,
            restored.queryForList("select * from collaboration_space_room_provisioning"),
        )
        assertEquals(
            "hris_collaboration_directory_reader",
            restored.queryForObject(
                "select viewowner from pg_views where viewname='collaboration_space_room_status'",
                String::class.java,
            ),
        )
        assertEquals(
            f.s.account,
            success(
                    f.s.run(f.s.actor.copy(accountId = f.peer)) {
                        f.rows("collaboration_space_room_status")
                    }
                )
                .single()["account_id"],
        )
    }

    @Test
    fun expiredRequesterAllowsPeerWithOwnProofAndKeepsBothPrivateHeads() {
        val f = Fixture()
        success(
            f.s.run {
                f.intent()
                f.enqueue(lifetimeSeconds = 1)
            }
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.s.run(f.s.actor.copy(accountId = f.peer)) { f.enqueue(account = f.peer) },
        )
        Thread.sleep(1100)
        assertTrue(success(f.worker { f.claim() }).isEmpty())
        val old = success(f.s.run { f.rows("collaboration_space_room_provisioning") }).single()
        assertEquals("REQUEST_EXPIRED", old["failure_code"])
        success(f.s.run(f.s.actor.copy(accountId = f.peer)) { f.enqueue(account = f.peer) })
        assertEquals(
            old,
            success(f.s.run { f.rows("collaboration_space_room_provisioning") }).single(),
        )
        val peer =
            success(
                    f.s.run(f.s.actor.copy(accountId = f.peer)) {
                        f.rows("collaboration_space_room_provisioning")
                    }
                )
                .single()
        assertEquals(f.peerParent, peer["native_session_id"])
        assertEquals(f.peerDevice, peer["device_id"])
        assertEquals(0L, peer["revision"])
        assertEquals(
            f.peer,
            success(f.s.run { f.rows("collaboration_space_room_status") }).single()["account_id"],
        )
        assertTrue(
            success(f.s.run(f.s.anonymous) { f.rows("collaboration_space_room_status") }).isEmpty()
        )
        for (column in
            listOf("native_session_id", "device_id", "provider_generation", "authenticated_at")) {
            assertEquals(
                false,
                f.s.owner.queryForObject(
                    "select has_column_privilege('hris_collaboration_directory_reader','collaboration_space_room_provisioning',?,'SELECT')",
                    Boolean::class.java,
                    column,
                ),
            )
        }
        assertInstanceOf(Result.Failed::class.java, f.s.run { f.enqueue(account = f.peer) })
        assertEquals(
            2,
            f.s.owner.queryForObject(
                "select count(*) from collaboration_space_room_provisioning",
                Int::class.java,
            ),
        )
    }

    @Test
    fun peerPublicationRetainsObservedCreatorAndRejectsStaleOrIncompletePublication() {
        val f = Fixture()
        success(
            f.s.run {
                f.intent()
                f.enqueue()
            }
        )
        val stale = success(f.worker { f.claim() }).single()
        success(f.s.run { f.cancel() })
        success(f.s.run(f.s.actor.copy(accountId = f.peer)) { f.enqueue(account = f.peer) })
        val current = success(f.worker { f.claim() }).single()
        assertEquals(f.peer, current["account_id"])
        assertInstanceOf(Result.Failed::class.java, f.worker(f.s.actor) { f.publish(stale) })
        assertInstanceOf(
            Result.Failed::class.java,
            f.worker(f.s.actor.copy(accountId = f.peer)) {
                f.publish(current, creator = UUID.randomUUID())
                f.finish(current)
            },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.worker(f.s.actor.copy(accountId = f.peer)) {
                f.publish(current, old = true)
                f.finish(current)
            },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.worker(f.s.actor.copy(accountId = f.peer)) { f.publish(current) },
        )
        assertEquals(
            0,
            f.s.owner.queryForObject(
                "select count(*) from collaboration_space_room_bindings",
                Int::class.java,
            ),
        )
        val rollback: Result<Unit> =
            f.workerTransactions.run(f.s.actor.copy(accountId = f.peer)) {
                safeDatabaseCall {
                        f.publish(current)
                        f.finish(current)
                    }
                    .flatMap {
                        Result.Failed(Failure(FailureKind.CONFLICT, "fixture_journal_failed"))
                    }
            }
        assertInstanceOf(Result.Failed::class.java, rollback)
        assertEquals(
            0,
            f.s.owner.queryForObject(
                "select count(*) from collaboration_space_room_bindings",
                Int::class.java,
            ),
        )
        success(
            f.worker(f.s.actor.copy(accountId = f.peer)) {
                f.publish(current)
                f.finish(current)
            }
        )
        val binding = success(f.s.run { f.rows("collaboration_space_room_bindings") }).single()
        assertEquals(f.peer, binding["published_by"])
        assertEquals(f.s.account, binding["matrix_creator_account_id"])
        assertEquals("!same-history", binding["matrix_room_id"])
        assertEquals(
            "SUCCEEDED",
            success(f.s.run { f.rows("collaboration_space_room_status") }).single()["state"],
        )
        assertEquals(0, success(f.worker(f.s.actor) { f.finish(stale) }))
        assertInstanceOf(Result.Failed::class.java, f.worker(f.s.actor) { f.publish(stale) })
        assertThrows(DataAccessException::class.java) {
            f.s.owner.update(
                "update collaboration_space_room_bindings set matrix_creator_account_id=?",
                f.peer,
            )
        }
    }

    @Test
    fun expiryAndClaimJoinTheExactParticipantWithoutChangingTheOtherTerminalHead() {
        val f = Fixture()
        success(f.s.run { f.intent() })
        success(
            f.s.run(f.s.actor.copy(accountId = f.peer)) {
                f.enqueue(account = f.peer)
                f.cancel(account = f.peer)
            }
        )
        val retained =
            success(
                    f.s.run(f.s.actor.copy(accountId = f.peer)) {
                        f.rows("collaboration_space_room_provisioning")
                    }
                )
                .single()
        success(f.s.run { f.enqueue(lifetimeSeconds = 1) })
        Thread.sleep(1100)
        assertTrue(success(f.worker { f.claim() }).isEmpty())
        assertEquals(
            retained,
            success(
                    f.s.run(f.s.actor.copy(accountId = f.peer)) {
                        f.rows("collaboration_space_room_provisioning")
                    }
                )
                .single(),
        )
        assertEquals(
            "FAILED",
            success(f.s.run { f.rows("collaboration_space_room_provisioning") }).single()["state"],
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.worker {
                f.worker.queryForList(
                    "select * from claim_collaboration_space_room_provisioning(?,9,30)",
                    UUID.randomUUID(),
                )
            },
        )
    }

    @Test
    fun simultaneousParticipantRequestsKeepOnlyOneActiveHeadThenAllowTerminalRecovery() {
        val f = Fixture()
        success(f.s.run { f.intent() })
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first =
                executor.submit<Result<Int>> {
                    f.s.run {
                        val n = f.enqueue()
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        n
                    }
                }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second =
                executor.submit<Result<Int>> {
                    f.s.run(f.s.actor.copy(accountId = f.peer)) { f.enqueue(account = f.peer) }
                }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
            var waiting = false
            while (System.nanoTime() < deadline && !waiting) {
                waiting =
                    f.s.owner.queryForObject(
                        "select exists(select 1 from pg_stat_activity where usename='space_gateway_fixture' and wait_event_type='Lock' and query like 'insert into collaboration_space_room_provisioning%')",
                        Boolean::class.java,
                    ) == true
                if (!waiting) Thread.sleep(10)
            }
            assertTrue(waiting)
            release.countDown()
            success(first.get(5, TimeUnit.SECONDS))
            assertInstanceOf(Result.Failed::class.java, second.get(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertEquals(
            1,
            f.s.owner.queryForObject(
                "select count(*) from collaboration_space_room_provisioning",
                Int::class.java,
            ),
        )
        success(f.s.run { f.cancel() })
        success(f.s.run(f.s.actor.copy(accountId = f.peer)) { f.enqueue(account = f.peer) })
        val claimed = success(f.worker { f.claim() }).single()
        assertEquals(f.peer, claimed["account_id"])
        assertEquals(
            "CANCELLED",
            success(f.s.run { f.rows("collaboration_space_room_provisioning") }).single()["state"],
        )
    }

    private inner class Fixture(target: String = "133") {
        val s = SpaceGatewayMigrationFixture(postgres)
        val peer = UUID.randomUUID()
        val peerParent = UUID.randomUUID()
        val peerDevice = UUID.randomUUID()
        val conversation = UUID.randomUUID()
        val pair = listOf(s.account, peer).sortedBy(UUID::toString)
        val worker = JdbcTemplate(s.workerSource)
        val workerTransactions =
            PostgresTransactionRunner(JdbcTransactionManager(s.workerSource), worker)

        init {
            s.owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional room peer')",
                peer,
                "$peer@example.test",
            )
            s.owner.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                s.company,
                peer,
            )
            s.owner.update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                s.company,
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
            Flyway.configure().dataSource(s.source).target("121").load().migrate()
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
            s.migrate()
            Flyway.configure().dataSource(s.source).target(target).load().migrate()
            s.owner.execute(
                "grant select,insert,update,delete on all tables in schema public to space_gateway_fixture,space_gateway_worker"
            )
            success(
                s.run(s.actor.copy(accountId = peer)) {
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
            )
        }

        fun user(account: UUID) =
            "@hrisg_${s.space.toString().replace("-", "")}_${account.toString().replace("-", "")}:matrix.example.test"

        fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

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

        fun <T> worker(actor: Actor = s.anonymous, body: () -> T): Result<T> =
            workerTransactions.run(actor) { safeDatabaseCall(body) }

        fun claim(seconds: Int = 30) =
            worker.queryForList(
                "select * from claim_collaboration_space_room_provisioning(?,1,?)",
                UUID.randomUUID(),
                seconds,
            )

        fun publish(
            lease: Map<String, Any?>,
            creator: UUID = s.account,
            old: Boolean = false,
        ): Int {
            val columns = if (old) "" else ",matrix_creator_account_id"
            val values = if (old) "" else ",?"
            val args =
                mutableListOf<Any?>(
                    s.space,
                    conversation,
                    lease["account_id"],
                    lease["revision"],
                    lease["lease_token"],
                    "!same-history",
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

        fun cancel(account: UUID = s.account) =
            s.runtime.update(
                "update collaboration_space_room_provisioning set state='CANCELLED',completed_at=statement_timestamp(),last_outcome_revision=revision,last_outcome_state='CANCELLED',last_outcome_at=statement_timestamp(),lease_owner=null,lease_token=null,lease_until=null where space_id=? and conversation_id=? and account_id=?",
                s.space,
                conversation,
                account,
            )
    }

    private fun <T> success(value: Result<T>): T = (value as Result.Success).value
}
