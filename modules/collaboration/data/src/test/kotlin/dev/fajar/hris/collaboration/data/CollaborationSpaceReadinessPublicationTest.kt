package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceKeyPublication
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Actual migrations, journal and key repository; every application operation uses normal RLS roles.
 */
@Testcontainers
class CollaborationSpaceReadinessPublicationTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun migrationPreservesOriginalPublicationAndCapturesOnlyNewJournalCommits() {
        val f = Fixture(migrate = false)
        success(f.publish(journal = false))
        val keys = f.rows("collaboration_space_key_publications")
        f.migrate()
        assertEquals(keys, f.rows("collaboration_space_key_publications"))
        assertTrue(f.rows().isEmpty())
        assertTrue(f.rows("collaboration_space_readiness_revisions").isEmpty())

        success(f.storage.tx { f.record() })
        val captured = f.rows()
        assertEquals(2, captured.size)
        assertEquals(setOf("MATRIX", "PUSH"), captured.map { it["channel"] }.toSet())
        assertEquals(1, captured.map { it["event_id"] }.toSet().size)
        assertEquals(setOf(1L), captured.map { it["publication_revision"] }.toSet())
        assertEquals(1, captured.map { it["captured_at"] }.toSet().size)
        assertEquals(setOf(f.storage.space), captured.map { it["space_id"] }.toSet())
        assertEquals(keys.single()["published_at"], captured.first()["publication_cutoff"])
        success(f.storage.tx { f.record() })
        assertEquals(captured, f.rows())
        assertEquals(1L, f.rows("collaboration_space_readiness_revisions").single()["revision"])

        // A selected HR company cannot become the organization scope, even for the same account.
        assertInstanceOf(
            Result.Failed::class.java,
            f.storage.tx(f.storage.actor.copy(companyId = f.storage.company)) { f.record() },
        )
        assertEquals(captured, f.rows())
    }

    @Test
    fun keyPublicationAuditAndBothChannelsRollBackTogetherOnFailureOrCancellation() {
        val f = Fixture()
        val auditBefore = f.rows("audit_entries").size
        val outboxBefore = f.rows("outbox_events").size
        for (cancel in listOf(false, true)) {
            val action = {
                f.storage.tx {
                    f.writePublication().flatMap {
                        f.record().flatMap {
                            assertEquals(
                                1,
                                f.storage.runtime.queryForObject(
                                    "select count(*) from outbox_events where event_type='collaboration.space_device_keys_published'",
                                    Int::class.java,
                                ),
                            )
                            assertTrue(f.rows().isEmpty(), "Uncommitted channels cannot escape")
                            if (cancel) throw CancellationException("fixture after journal")
                            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_rollback"))
                        }
                    }
                }
            }
            if (cancel) assertThrows(CancellationException::class.java) { action() }
            else assertInstanceOf(Result.Failed::class.java, action())
            assertTrue(f.rows().isEmpty())
            assertTrue(f.rows("collaboration_space_readiness_revisions").isEmpty())
            assertTrue(f.rows("collaboration_space_key_publications").isEmpty())
            assertEquals(auditBefore, f.rows("audit_entries").size)
            assertEquals(outboxBefore, f.rows("outbox_events").size)
        }
        success(f.publish())
        assertEquals(2, f.rows().size)
        assertEquals(1L, f.rows().first()["publication_revision"])
    }

    @Test
    fun unconfirmedOrSubstitutedSourceCannotCreateReadinessAndApiCannotReadWorkerQueue() {
        val f = Fixture()
        assertInstanceOf(Result.Failed::class.java, f.storage.tx { f.record() })
        success(f.storage.tx { f.exchange.keys.reserve(f.value()) })
        assertInstanceOf(Result.Failed::class.java, f.storage.tx { f.record() })
        success(
            f.storage.tx {
                f.exchange.keys.find(f.storage.space, f.storage.account, f.storage.device).flatMap {
                    f.exchange.keys.publish(requireNotNull(it), now())
                }
            }
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.storage.tx { f.record(device = UUID.randomUUID()) },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.storage.tx(f.exchange.peerActor) { f.record() },
        )
        assertTrue(f.rows().isEmpty())
        success(f.storage.tx { f.record() })
        assertEquals(
            0,
            success(
                f.storage.tx {
                    safeDatabaseCall {
                        f.storage.runtime.queryForObject(
                            "select count(*) from collaboration_space_readiness_deliveries",
                            Int::class.java,
                        )
                    }
                }
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.storage.tx {
                safeDatabaseCall {
                    f.storage.runtime.queryForList(
                        "select * from claim_collaboration_space_readiness('MATRIX',?::uuid,1)",
                        UUID.randomUUID(),
                    )
                }
            },
        )
        for (role in
            listOf(
                "hris_collaboration_space_readiness_capture",
                "hris_collaboration_space_readiness_reader",
            )) {
            for ((table, column) in
                listOf(
                    "outbox_events" to "payload",
                    "collaboration_space_key_publications" to "fingerprint",
                    "collaboration_space_provider_sessions" to "token_encrypted",
                )) {
                assertFalse(
                    f.storage.owner.queryForObject(
                        "select has_column_privilege(?,?,?,'SELECT')",
                        Boolean::class.java,
                        role,
                        table,
                        column,
                    )!!
                )
            }
        }
    }

    @Test
    fun revisionsFollowCommitOrderAndRollbackDoesNotLeaveAGap() {
        val f = Fixture()
        val written = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first =
                executor.submit<Result<Unit>> {
                    f.storage.tx {
                        f.writePublication()
                            .flatMap { f.record() }
                            .flatMap {
                                written.countDown()
                                check(release.await(10, TimeUnit.SECONDS))
                                Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_rollback"))
                            }
                    }
                }
            assertTrue(written.await(10, TimeUnit.SECONDS))
            val second =
                executor.submit<Result<Unit>> {
                    secondStarted.countDown()
                    f.publish(peer = true)
                }
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { second.get(200, TimeUnit.MILLISECONDS) }
            assertTrue(f.rows().isEmpty())
            release.countDown()
            assertInstanceOf(Result.Failed::class.java, first.get(10, TimeUnit.SECONDS))
            success(second.get(10, TimeUnit.SECONDS))
            assertEquals(
                setOf(f.exchange.peer),
                f.rows().map { it["publisher_account_id"] }.toSet(),
            )
            assertEquals(setOf(1L), f.rows().map { it["publication_revision"] }.toSet())
            success(f.publish())
            assertEquals(
                setOf(2L),
                f.rows()
                    .filter { it["publisher_account_id"] == f.storage.account }
                    .map { it["publication_revision"] }
                    .toSet(),
            )
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun channelsLeaseIndependentlyAndStopAfterEightAttemptsWithoutProgress() {
        val f = Fixture()
        success(f.publish())
        val first = success(f.claim("MATRIX")).single()
        assertTrue(success(f.claim("MATRIX")).isEmpty())
        val push = success(f.claim("PUSH")).single()
        assertNotEquals(first["lease_token"], push["lease_token"])
        assertEquals(first["event_id"], push["event_id"])
        assertEquals(1, success(f.release(push, complete = true)))
        assertTrue(success(f.claim("PUSH")).isEmpty())
        var current = first
        for (attempt in 1..8) {
            assertEquals(attempt, current["attempts"])
            assertEquals(1, success(f.release(current)))
            assertEquals(0, success(f.release(current)), "A released token cannot write again")
            if (attempt < 8) current = success(f.claim("MATRIX")).single()
        }
        assertTrue(success(f.claim("MATRIX")).isEmpty())
        for (bad in listOf(0, 5, Int.MAX_VALUE)) assertInstanceOf(
            Result.Failed::class.java,
            f.claim("MATRIX", limit = bad),
        )
        assertInstanceOf(Result.Failed::class.java, f.claim("unknown"))
        assertInstanceOf(
            Result.Failed::class.java,
            f.claim("PUSH", actor = f.storage.actor.copy(companyId = f.storage.company)),
        )
        assertEquals(
            0,
            success(
                f.storage.tx {
                    safeDatabaseCall {
                        f.storage.runtime.update(
                            "update collaboration_space_readiness_deliveries set attempts=0"
                        )
                    }
                }
            ),
        )
    }

    @Test
    fun targetsRequireOriginalPeerRoomCurrentReceivingParentAndExactClaimedScope() {
        val f = Fixture(migrate = false)
        success(f.publish(peer = true, journal = false))
        f.migrate()
        f.exchange.bind()
        success(f.publish())
        val lease = success(f.claim("MATRIX")).single()
        val targets = success(f.targets(lease))
        assertEquals(1, targets.size)
        assertEquals(f.exchange.peer, targets.single()["target_account_id"])
        assertEquals(f.exchange.peerDevice, targets.single()["target_device_id"])
        assertEquals(f.exchange.peerParent, targets.single()["native_session_id"])
        assertTrue(success(f.targets(lease, f.exchange.peerActor)).isEmpty())
        assertTrue(
            success(f.targets(lease, f.storage.actor.copy(companyId = f.storage.company))).isEmpty()
        )
        assertTrue(success(f.targets(lease + ("lease_token" to UUID.randomUUID()))).isEmpty())

        f.storage.owner.update(
            "update native_sessions set access_expires_at=clock_timestamp()-interval '1 second' where id=?",
            f.exchange.peerParent,
        )
        assertEquals(
            targets,
            success(f.targets(lease)),
            "Short access expiry does not erase receiving readiness",
        )
        f.storage.owner.update(
            "update native_sessions set revoked_at=clock_timestamp() where id=?",
            f.exchange.peerParent,
        )
        assertTrue(success(f.targets(lease)).isEmpty())
        assertEquals(1, success(f.release(lease, complete = true)))
        assertTrue(success(f.targets(lease)).isEmpty())
    }

    @Test
    fun competingClaimersRespectChannelCapacityAndDumpRestoreRetainsExactProgress() {
        val f = Fixture(extraDevices = 9)
        success(f.publish())
        for ((device, parent) in f.extra) success(
            f.storage.tx {
                val key =
                    f.value()
                        .copy(
                            deviceId = device,
                            nativeSessionId = parent,
                            matrixDeviceId = "HRISG_${device.toString().replace("-", "")}",
                        )
                f.exchange.keys
                    .reserve(key)
                    .flatMap { f.exchange.keys.publish(key, now()) }
                    .flatMap { f.record(device = device) }
            }
        )
        assertEquals(20, f.rows().size)
        val barrier = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val claimed =
            try {
                    val jobs =
                        (1..2).map {
                            executor.submit<List<Map<String, Any?>>> {
                                check(barrier.await(5, TimeUnit.SECONDS))
                                success(f.claim("MATRIX", limit = 4))
                            }
                        }
                    barrier.countDown()
                    jobs.flatMap { it.get(10, TimeUnit.SECONDS) }
                } finally {
                    barrier.countDown()
                    executor.shutdownNow()
                    assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
                }
                .toMutableList()
        // A competing advisory-lock loser may return empty; the next bounded poll fills capacity.
        if (claimed.size < 8) claimed += success(f.claim("MATRIX", limit = 4))
        assertEquals(8, claimed.size)
        assertEquals(8, claimed.map { it["event_id"] }.toSet().size)
        assertEquals(8, claimed.map { it["lease_token"] }.toSet().size)
        assertTrue(success(f.claim("MATRIX", limit = 4)).isEmpty())
        assertEquals(4, success(f.claim("PUSH", limit = 4)).size)
        assertEquals(1, success(f.release(claimed.first(), complete = true)))
        assertEquals(1, success(f.claim("MATRIX", limit = 4)).size)

        val before = f.rows()
        val originalKeys = f.rows("collaboration_space_key_publications")
        val restored = f.storage.restore()
        // Same ordinary worker role against the restored database, no owner or credential reads.
        restored.username = "space_gateway_worker"
        restored.password = "isolated-worker-only"
        assertEquals(
            before,
            JdbcTemplate(restored)
                .queryForList("select * from collaboration_space_readiness_deliveries order by 1,2"),
        )
        assertEquals(originalKeys, f.rows("collaboration_space_key_publications"))
    }

    @Test
    fun progressRequiresForwardCursorAndAnUnexpiredLeaseOfTheSamePublisher() {
        val f = Fixture()
        success(f.publish())
        val lease = success(f.claim("MATRIX")).single()
        val next = f.exchange.peer
        fun advance(
            account: UUID = next,
            device: UUID = f.exchange.peerDevice,
            actor: Actor = f.storage.actor,
        ) =
            f.exchange.workerTransactions.run(actor) {
                safeDatabaseCall {
                    f.exchange.worker.update(
                        """update collaboration_space_readiness_deliveries
                    set after_account_id=?,after_device_id=?,attempts=0
                    where event_id=? and channel=? and lease_token=?""",
                        account,
                        device,
                        lease["event_id"],
                        lease["channel"],
                        lease["lease_token"],
                    )
                }
            }
        assertInstanceOf(Result.Failed::class.java, advance(actor = f.exchange.peerActor))
        assertInstanceOf(Result.Failed::class.java, advance(account = f.storage.account))
        assertEquals(1, success(advance()))
        val progressed = f.rows().single { it["channel"] == "MATRIX" }
        assertEquals(0, progressed["attempts"])
        assertInstanceOf(Result.Failed::class.java, advance())
        val earlier = UUID(0, 1)
        assertInstanceOf(Result.Failed::class.java, advance(account = earlier))
        assertInstanceOf(
            Result.Failed::class.java,
            f.release(progressed, actor = f.exchange.peerActor),
        )
        assertEquals(1, success(f.release(progressed)))
        val replacement = success(f.claim("MATRIX")).single()
        assertNotEquals(lease["lease_token"], replacement["lease_token"])
        assertEquals(1, replacement["attempts"])
        assertEquals(0, success(f.release(lease)))
        assertEquals(0, success(advance()))
        // Expire only this disposable fixture row. Runtime cannot shorten or renew a lease.
        assertInstanceOf(
            Result.Failed::class.java,
            f.exchange.workerTransactions.run(f.storage.actor) {
                safeDatabaseCall {
                    f.exchange.worker.update(
                        "update collaboration_space_readiness_deliveries set lease_until=clock_timestamp()-interval '1 second' where event_id=? and channel='MATRIX'",
                        lease["event_id"],
                    )
                }
            },
        )
        f.storage.owner.execute(
            "alter table collaboration_space_readiness_deliveries disable trigger protect_collaboration_space_readiness_delivery"
        )
        try {
            f.storage.owner.update(
                "update collaboration_space_readiness_deliveries set lease_until=clock_timestamp()-interval '1 second' where event_id=? and channel='MATRIX'",
                lease["event_id"],
            )
        } finally {
            f.storage.owner.execute(
                "alter table collaboration_space_readiness_deliveries enable trigger protect_collaboration_space_readiness_delivery"
            )
        }
        assertEquals(0, success(f.release(replacement)))
        assertNotEquals(
            replacement["lease_token"],
            success(f.claim("MATRIX")).single()["lease_token"],
        )
    }

    @Test
    fun aRoomBoundAfterPublicationIsNotAddedToTheCapturedFanout() {
        val f = Fixture(migrate = false)
        success(f.publish(peer = true, journal = false))
        f.migrate()
        success(f.publish())
        f.exchange.bind()
        val lease = success(f.claim("MATRIX")).single()
        assertTrue(success(f.targets(lease)).isEmpty())
    }

    @Test
    fun receiverPublicationCommittedLaterCannotEnterAnEarlierRevisionThroughItsOlderTimestamp() {
        val f = Fixture()
        f.exchange.bind()
        val written = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val receiver =
                executor.submit<Result<Unit>> {
                    f.storage.tx(f.exchange.peerActor) {
                        f.writePublication(peer = true).flatMap {
                            written.countDown()
                            check(release.await(15, TimeUnit.SECONDS))
                            f.record(peer = true)
                        }
                    }
                }
            assertTrue(written.await(10, TimeUnit.SECONDS))
            success(f.publish())
            release.countDown()
            success(receiver.get(10, TimeUnit.SECONDS))
            val own =
                f.rows().single {
                    it["channel"] == "MATRIX" && it["publisher_account_id"] == f.storage.account
                }
            val peer =
                f.rows().single {
                    it["channel"] == "MATRIX" && it["publisher_account_id"] == f.exchange.peer
                }
            assertTrue(
                (peer["publication_cutoff"] as java.sql.Timestamp).before(
                    own["captured_at"] as java.sql.Timestamp
                )
            )
            assertEquals(1L, own["publication_revision"])
            assertEquals(2L, peer["publication_revision"])
            val leases = success(f.claim("MATRIX", limit = 2))
            assertEquals(2, leases.size)
            val ownLease = leases.single { it["publisher_account_id"] == f.storage.account }
            assertTrue(
                success(f.targets(ownLease)).isEmpty(),
                "Later-committed receiver must be outside the captured revision",
            )
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private inner class Fixture(migrate: Boolean = true, extraDevices: Int = 0) {
        val extra = List(extraDevices) { UUID.randomUUID() to UUID.randomUUID() }
        val exchange =
            SpaceKeyExchangeFixture(postgres) { s ->
                // Upgrade existing pre-queue SPACE devices; no production guard is disabled.
                for ((device, parent) in extra) {
                    s.owner.update(
                        """insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,
                    credential_version,access_expires_at,created_at,rotated_at)
                    select ?,account_id,?,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at
                    from native_sessions where id=?""",
                        parent,
                        parent.toString().replace("-", "").repeat(2),
                        s.parent,
                    )
                    s.owner.update(
                        """insert into collaboration_space_devices(space_id,id,account_id,matrix_user_id,matrix_device_id,
                    origin,origin_native_session_id,platform,label,state,version,created_at,updated_at)
                    select space_id,?,account_id,matrix_user_id,?,'SPACE',?,platform,label,'PROVISIONING',0,created_at,created_at
                    from collaboration_space_devices where id=?""",
                        device,
                        "HRISG_${device.toString().replace("-", "")}",
                        parent,
                        s.device,
                    )
                    s.owner.update(
                        """insert into collaboration_space_device_sessions(space_id,device_id,account_id,native_session_id,bound_at)
                    select space_id,?,account_id,?,bound_at from collaboration_space_device_sessions where device_id=?""",
                        device,
                        parent,
                        s.device,
                    )
                    s.owner.update(
                        """insert into collaboration_space_provider_sessions(space_id,device_id,account_id,native_session_id,
                    matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at)
                    select space_id,?,account_id,?,matrix_user_id,?,0,'owned-extra-fixture',created_at,updated_at,token_expires_at
                    from collaboration_space_provider_sessions where device_id=?""",
                        device,
                        parent,
                        "HRISG_${device.toString().replace("-", "")}",
                        s.device,
                    )
                    s.owner.update(
                        "update collaboration_space_devices set state='ACTIVE',version=1 where id=?",
                        device,
                    )
                }
            }
        val storage = exchange.s

        init {
            if (migrate) migrate()
        }

        fun migrate() {
            Flyway.configure().dataSource(storage.source).target("139").load().migrate()
            // Broad application grants still cannot override FORCE RLS or the capability check.
            storage.owner.execute(
                "grant select,insert,update,delete on all tables in schema public to space_gateway_fixture,space_gateway_worker"
            )
        }

        fun value(peer: Boolean = false) =
            CollaborationSpaceKeyPublication(
                storage.space,
                if (peer) exchange.peer else storage.account,
                if (peer) exchange.peerDevice else storage.device,
                if (peer) exchange.peerParent else storage.parent,
                if (peer) exchange.peerKeys.userId else storage.user,
                if (peer) exchange.peerKeys.deviceId
                else "HRISG_${storage.device.toString().replace("-", "")}",
                "a".repeat(64),
                0,
                now(),
            )

        fun writePublication(peer: Boolean = false): Result<Unit> {
            val key = value(peer)
            return exchange.keys.reserve(key).flatMap {
                exchange.keys.publish(key, now()).map { Unit }
            }
        }

        fun record(
            peer: Boolean = false,
            device: UUID = if (peer) exchange.peerDevice else storage.device,
        ) =
            exchange.base.journal.record(
                if (peer) exchange.peerActor else storage.actor,
                ChangeRecord(
                    "collaboration_space_device",
                    device,
                    "collaboration.space_device_keys_published",
                ),
            )

        fun publish(peer: Boolean = false, journal: Boolean = true) =
            storage.tx(if (peer) exchange.peerActor else storage.actor) {
                writePublication(peer).flatMap {
                    if (journal) record(peer) else Result.Success(Unit)
                }
            }

        fun rows(table: String = "collaboration_space_readiness_deliveries") =
            storage.owner.queryForList("select * from $table order by 1,2")

        fun claim(channel: String, limit: Int = 1, actor: Actor = storage.actor) =
            exchange.workerTransactions.run(actor) {
                safeDatabaseCall {
                    exchange.worker.queryForList(
                        "select * from claim_collaboration_space_readiness(?::varchar,?::uuid,?)",
                        channel,
                        UUID.randomUUID(),
                        limit,
                    )
                }
            }

        fun release(
            lease: Map<String, Any?>,
            complete: Boolean = false,
            actor: Actor = storage.actor,
        ) =
            exchange.workerTransactions.run(actor) {
                safeDatabaseCall {
                    exchange.worker.update(
                        """update collaboration_space_readiness_deliveries
                    set lease_owner=null,lease_token=null,lease_until=null,available_at=clock_timestamp(),
                        completed_at=case when ? then clock_timestamp() else null end
                    where event_id=? and channel=? and lease_owner=? and lease_token=? and lease_until=?
                        and lease_until>clock_timestamp() and completed_at is null""",
                        complete,
                        lease["event_id"],
                        lease["channel"],
                        lease["lease_owner"],
                        lease["lease_token"],
                        lease["lease_until"],
                    )
                }
            }

        fun targets(lease: Map<String, Any?>, actor: Actor = storage.actor) =
            exchange.workerTransactions.run(actor) {
                safeDatabaseCall {
                    exchange.worker.queryForList(
                        "select * from collaboration_space_readiness_targets where event_id=? and channel=? and lease_token=?",
                        lease["event_id"],
                        lease["channel"],
                        lease["lease_token"],
                    )
                }
            }
    }

    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
