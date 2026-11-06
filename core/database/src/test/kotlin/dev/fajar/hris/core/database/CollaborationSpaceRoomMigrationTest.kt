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
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceRoomMigrationTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun roomIntentRequiresCanonicalPairAndQueueStatusDoesNotExposeNativeProofToPeer() {
        val f = Fixture()
        for (actor in
            listOf(
                f.s.anonymous,
                f.s.actor.copy(companyId = f.s.company),
                f.s.actor.copy(accountId = f.peer),
            )) {
            assertInstanceOf(Result.Failed::class.java, f.s.run(actor) { f.intent() })
        }
        assertInstanceOf(
            Result.Failed::class.java,
            f.s.run { f.intent(firstUser = "@foreign:matrix.example.test") },
        )
        assertEquals(1, success(f.s.run { f.intent() }))
        for ((device, generation, native) in
            listOf(Triple(2L, 0L, 0L), Triple(1L, 1L, 0L), Triple(1L, 0L, 1L))) {
            assertInstanceOf(
                Result.Failed::class.java,
                f.s.run {
                    f.enqueue(
                        deviceVersion = device,
                        generation = generation,
                        nativeVersion = native,
                    )
                },
            )
        }
        assertEquals(1, success(f.s.run { f.enqueue() }))
        assertEquals(1, success(f.s.run { f.rows("collaboration_space_room_provisioning").size }))
        assertEquals(
            0,
            success(
                f.s.run(f.s.actor.copy(accountId = f.peer)) {
                    f.rows("collaboration_space_room_provisioning").size
                }
            ),
        )
        val status =
            success(
                    f.s.run(f.s.actor.copy(accountId = f.peer)) {
                        f.rows("collaboration_space_room_status")
                    }
                )
                .single()
        assertEquals("QUEUED", status["state"])
        assertFalse(
            status.keys.any {
                it in
                    setOf(
                        "native_session_id",
                        "authenticated_at",
                        "mfa_verified_at",
                        "device_id",
                        "provider_generation",
                    )
            }
        )
        for (actor in
            listOf(
                f.s.anonymous,
                f.s.actor.copy(companyId = f.s.company),
                f.s.actor.copy(accountId = UUID.randomUUID()),
            )) {
            assertTrue(
                success(f.s.run(actor) { f.rows("collaboration_space_room_status") }).isEmpty()
            )
        }
        for (column in
            listOf("native_session_id", "authenticated_at", "device_id", "provider_generation")) {
            assertEquals(
                false,
                f.s.owner.queryForObject(
                    "select has_column_privilege('hris_collaboration_directory_reader','collaboration_space_room_provisioning',?,'SELECT')",
                    Boolean::class.java,
                    column,
                ),
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            f.s.run { f.s.runtime.execute("set local role hris_collaboration_directory_reader") },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.s.run {
                f.s.runtime.queryForList(
                    "select * from claim_collaboration_space_room_provisioning(?,1,30)",
                    UUID.randomUUID(),
                )
            },
        )
        assertThrows(DataAccessException::class.java) {
            f.s.owner.update(
                "update collaboration_space_room_intents set matrix_creator_account_id=?",
                f.peer,
            )
        }
        assertThrows(DataAccessException::class.java) {
            f.s.owner.update("delete from collaboration_space_room_intents")
        }
        f.s.owner.update("delete from native_sessions where id=?", f.s.parent)
        assertEquals(
            status,
            success(
                    f.s.run(f.s.actor.copy(accountId = f.peer)) {
                        f.rows("collaboration_space_room_status")
                    }
                )
                .single(),
        )
        assertEquals(1, success(f.s.run { f.cancel() }))
    }

    @Test
    fun concurrentClaimsAndExpiredLeasesCannotPublishOverAReplacement() {
        val f = Fixture()
        success(
            f.s.run {
                f.intent()
                f.enqueue()
            }
        )
        val pool = Executors.newFixedThreadPool(2)
        try {
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val futures =
                (0..1).map {
                    pool.submit<List<Map<String, Any?>>> {
                        ready.countDown()
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        success(f.worker { f.claim(seconds = 1) })
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val leases = futures.flatMap { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, leases.size)
            val first = leases.single()
            assertEquals(1, first["attempts"])
            val deadline = (first["lease_until"] as java.sql.Timestamp).toInstant()
            val pause =
                java.time.Duration.between(Instant.now(), deadline).toMillis().coerceAtLeast(0) + 30
            Thread.sleep(pause)
            val next = success(f.worker { f.claim() }).single()
            assertEquals(2, next["attempts"])
            assertNotEquals(first["lease_token"], next["lease_token"])
            assertInstanceOf(Result.Failed::class.java, f.worker(f.s.actor) { f.publish(first) })
            assertTrue(success(f.s.run { f.rows("collaboration_space_room_bindings") }).isEmpty())
            f.s.owner.update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                f.peer,
            )
            assertInstanceOf(Result.Failed::class.java, f.worker(f.s.actor) { f.publish(next) })
            assertTrue(success(f.s.run { f.rows("collaboration_space_room_bindings") }).isEmpty())
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun publicationAndOutcomeRollBackTogetherAndCompetingPublishersCannotReplaceRoom() {
        val f = Fixture()
        success(
            f.s.run {
                f.intent()
                f.enqueue()
            }
        )
        val lease = success(f.worker { f.claim() }).single()
        assertInstanceOf(Result.Failed::class.java, f.worker(f.s.actor) { f.finish(lease) })
        assertInstanceOf(Result.Failed::class.java, f.worker(f.s.actor) { f.publish(lease) })
        assertTrue(success(f.s.run { f.rows("collaboration_space_room_bindings") }).isEmpty())
        val denied =
            f.workerTransactions.run<Unit>(f.s.actor) {
                f.publish(lease)
                f.finish(lease)
                Result.Failed(Failure(FailureKind.CONFLICT, "fixture_journal_failed"))
            }
        assertInstanceOf(Result.Failed::class.java, denied)
        assertTrue(success(f.s.run { f.rows("collaboration_space_room_bindings") }).isEmpty())
        assertEquals(
            "RUNNING",
            success(f.s.run { f.rows("collaboration_space_room_status") }).single()["state"],
        )
        for (interrupt in listOf(false, true)) {
            val call = {
                f.workerTransactions.run<Unit>(f.s.actor) {
                    f.publish(lease)
                    if (interrupt) throw InterruptedException("fixture")
                    throw CancellationException("fixture")
                }
            }
            if (interrupt) assertThrows(InterruptedException::class.java) { call() }
            else assertThrows(CancellationException::class.java) { call() }
            assertTrue(success(f.s.run { f.rows("collaboration_space_room_bindings") }).isEmpty())
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val futures =
                (0..1).map { index ->
                    pool.submit<Result<Int>> {
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        f.worker(f.s.actor) {
                            f.publish(lease, "!candidate$index")
                            f.finish(lease)
                        }
                    }
                }
            start.countDown()
            val outcomes = futures.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, outcomes.count { it is Result.Success })
            assertEquals(1, outcomes.count { it is Result.Failed })
            val bound = success(f.s.run { f.rows("collaboration_space_room_bindings") }).single()
            assertEquals(
                bound,
                success(
                        f.s.run(f.s.actor.copy(accountId = f.peer)) {
                            f.rows("collaboration_space_room_bindings")
                        }
                    )
                    .single(),
            )
            assertEquals(
                "SUCCEEDED",
                success(f.s.run { f.rows("collaboration_space_room_status") }).single()["state"],
            )
            assertInstanceOf(Result.Failed::class.java, f.s.run { f.retry() })
            assertThrows(DataAccessException::class.java) {
                f.s.owner.update(
                    "update collaboration_space_room_bindings set matrix_room_id='!replacement'"
                )
            }
            assertThrows(DataAccessException::class.java) {
                f.s.owner.update("delete from collaboration_space_room_bindings")
            }
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun archiveRestoresOriginalIntentBindingStatusAndRestrictedViewOwnerAfterParentPurge() {
        val f = Fixture()
        success(
            f.s.run {
                f.intent()
                f.enqueue()
            }
        )
        val lease = success(f.worker { f.claim() }).single()
        success(
            f.worker(f.s.actor) {
                f.publish(lease)
                f.finish(lease)
            }
        )
        val expected = success(f.s.run { f.rows("collaboration_space_room_status") })
        val binding = success(f.s.run { f.rows("collaboration_space_room_bindings") })
        f.s.owner.update("delete from native_sessions where id=?", f.s.parent)
        assertEquals(expected, success(f.s.run { f.rows("collaboration_space_room_status") }))
        val owner = f.s.restore()
        val source =
            DriverManagerDataSource(
                postgres.jdbcUrl.substringBeforeLast('/') + "/space_gateway_restored",
                "space_gateway_fixture",
                "isolated-fixture-only",
            )
        val sql = JdbcTemplate(source)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(source), sql)
        assertEquals(
            expected,
            success(
                transactions.run(f.s.actor.copy(accountId = f.peer)) {
                    safeDatabaseCall {
                        sql.queryForList("select * from collaboration_space_room_status")
                    }
                }
            ),
        )
        assertEquals(
            binding,
            success(
                transactions.run(f.s.actor) {
                    safeDatabaseCall {
                        sql.queryForList("select * from collaboration_space_room_bindings")
                    }
                }
            ),
        )
        assertTrue(
            success(
                    transactions.run(f.s.anonymous) {
                        safeDatabaseCall {
                            sql.queryForList("select * from collaboration_space_room_status")
                        }
                    }
                )
                .isEmpty()
        )
        assertEquals(
            "hris_collaboration_directory_reader",
            owner.queryForObject(
                "select pg_get_userbyid(relowner) from pg_class where oid='collaboration_space_room_status'::regclass",
                String::class.java,
            ),
        )
        assertEquals(
            false,
            owner.queryForObject(
                "select has_column_privilege('hris_collaboration_directory_reader','collaboration_space_room_provisioning','native_session_id','SELECT')",
                Boolean::class.java,
            ),
        )
        for (table in
            listOf(
                "collaboration_space_room_intents",
                "collaboration_space_room_provisioning",
                "collaboration_space_room_bindings",
            )) {
            assertEquals(
                true,
                owner.queryForObject(
                    "select relrowsecurity and relforcerowsecurity from pg_class where oid=?::regclass",
                    Boolean::class.java,
                    table,
                ),
            )
            assertThrows(DataAccessException::class.java) { owner.update("delete from $table") }
        }
    }

    @Test
    fun legacyIntentKeepsTheActualCreatorAndCannotReplaceAnExistingOriginalRoom() {
        val f = Fixture()
        val creator = UUID.randomUUID()
        val requester = UUID.randomUUID()
        val pair = listOf(creator, requester).sortedBy(UUID::toString)
        val devices = mapOf(creator to UUID.randomUUID(), requester to UUID.randomUUID())
        val parents = mapOf(creator to UUID.randomUUID(), requester to UUID.randomUUID())
        val company = f.s.company
        val at = f.s.at.toString()
        fun compact(id: UUID) = id.toString().replace("-", "")
        fun user(account: UUID) =
            "@hris_${compact(company)}_${compact(account)}:matrix.example.test"
        val db = f.s.owner
        for (account in pair) {
            val device = devices.getValue(account)
            val parent = parents.getValue(account)
            db.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional retained room participant')",
                account,
                "$account@example.test",
            )
            db.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
            db.update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                company,
                account,
            )
            db.update(
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
                parent,
                account,
                compact(parent).repeat(2),
                f.s.at.minusSeconds(60).toString(),
                f.s.at.plusSeconds(3600).toString(),
                f.s.at.plusSeconds(900).toString(),
                f.s.at.minusSeconds(60).toString(),
                f.s.at.minusSeconds(30).toString(),
            )
            db.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional retained room device','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                company,
                device,
                account,
                at,
                at,
            )
            db.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                company,
                device,
                account,
                parent,
                at,
            )
            db.update(
                "update collaboration_devices set state='ACTIVE',version=1 where company_id=? and id=?",
                company,
                device,
            )
            db.update(
                "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'fixture-envelope',?::timestamptz,?::timestamptz,?::timestamptz)",
                company,
                device,
                account,
                user(account),
                "HRIS_${compact(device)}",
                at,
                at,
                f.s.at.plusSeconds(600).toString(),
            )
            db.update(
                "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,legacy_company_id,legacy_device_id,created_at) values(?,?,'matrix.example.test',?,'LEGACY',?,?,?::timestamptz)",
                f.s.space,
                account,
                user(account),
                company,
                device,
                at,
            )
        }
        val original = UUID.randomUUID()
        val group = UUID.randomUUID()
        val job = UUID.randomUUID()
        db.update(
            "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?::timestamptz)",
            company,
            original,
            pair[0],
            pair[1],
            requester,
            at,
        )
        db.update(
            "insert into collaboration_space_direct_conversations(space_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?::timestamptz)",
            f.s.space,
            group,
            pair[0],
            pair[1],
            requester,
            at,
        )
        db.update(
            """insert into background_jobs(id,company_id,actor_id,kind,operation_id,request,authenticated_at,credential_version,correlation_id,created_at,total_items)
            values(?,?,?,'COLLABORATION_DIRECT_PROVISION',?,jsonb_build_object('conversationId',?::text,'deviceId',?::text,'nativeSessionId',?::text,'platform','ANDROID','clientBuild','28'),?::timestamptz,0,?,?::timestamptz,1)""",
            job,
            company,
            creator,
            UUID.randomUUID(),
            original,
            devices.getValue(creator),
            parents.getValue(creator),
            f.s.at.minusSeconds(60).toString(),
            UUID.randomUUID(),
            at,
        )
        db.update(
            "insert into collaboration_direct_room_provisioning(company_id,conversation_id,account_id,device_id,job_id,version,created_at,updated_at) values(?,?,?,?,?,0,?::timestamptz,?::timestamptz)",
            company,
            original,
            creator,
            devices.getValue(creator),
            job,
            at,
            at,
        )
        db.update(
            "update background_jobs set status='RUNNING',attempts=1,lease_owner=gen_random_uuid(),lease_token=gen_random_uuid(),lease_until=now()+interval '1 minute' where id=?",
            job,
        )
        db.update(
            "insert into collaboration_direct_room_bindings(company_id,conversation_id,account_id,job_id,provisioning_version,matrix_server_name,matrix_room_id,created_at) values(?,?,?,?,0,'matrix.example.test','!legacy:matrix.example.test',?::timestamptz)",
            company,
            original,
            creator,
            job,
            at,
        )
        val prior = db.queryForList("select * from collaboration_direct_room_bindings")
        fun insert(
            origin: String = "LEGACY",
            declaredCreator: UUID = creator,
            room: String = "!legacy:matrix.example.test",
        ) =
            f.s.run(f.s.actor.copy(accountId = requester)) {
                f.s.runtime.update(
                    "insert into collaboration_space_room_intents(space_id,conversation_id,first_account_id,second_account_id,requested_by,matrix_creator_account_id,matrix_server_name,first_matrix_user_id,second_matrix_user_id,origin,legacy_company_id,legacy_conversation_id,legacy_room_id,legacy_bound_at,created_at) values(?,?,?,?,?,?,'matrix.example.test',?,?,?,?,?,?,?::timestamptz,?::timestamptz)",
                    f.s.space,
                    group,
                    pair[0],
                    pair[1],
                    requester,
                    declaredCreator,
                    user(pair[0]),
                    user(pair[1]),
                    origin,
                    company.takeIf { origin == "LEGACY" },
                    original.takeIf { origin == "LEGACY" },
                    room.takeIf { origin == "LEGACY" },
                    at.takeIf { origin == "LEGACY" },
                    at,
                )
            }
        assertInstanceOf(Result.Failed::class.java, insert(declaredCreator = requester))
        assertInstanceOf(Result.Failed::class.java, insert(room = "!replacement"))
        assertInstanceOf(
            Result.Failed::class.java,
            insert(origin = "SPACE", declaredCreator = requester),
        )
        assertEquals(1, success(insert()))
        val intent =
            success(
                f.s.run(f.s.actor.copy(accountId = creator)) {
                    f.s.runtime.queryForMap(
                        "select * from collaboration_space_room_intents where conversation_id=?",
                        group,
                    )
                }
            )
        assertEquals(creator, intent["matrix_creator_account_id"])
        assertEquals(requester, intent["requested_by"])
        assertEquals(original, intent["legacy_conversation_id"])
        assertEquals("!legacy:matrix.example.test", intent["legacy_room_id"])
        for (parent in parents.values) db.update("delete from native_sessions where id=?", parent)
        assertEquals(
            intent,
            success(
                f.s.run(f.s.actor.copy(accountId = requester)) {
                    f.s.runtime.queryForMap(
                        "select * from collaboration_space_room_intents where conversation_id=?",
                        group,
                    )
                }
            ),
        )
        assertEquals(prior, db.queryForList("select * from collaboration_direct_room_bindings"))
    }

    @Test
    fun finiteAttemptsRetainFailureAndExplicitRetryPublishesOnlyTheNewRevision() {
        val f = Fixture()
        success(
            f.s.run {
                f.intent()
                f.enqueue()
            }
        )
        var first: Map<String, Any?>? = null
        for (attempt in 1..8) {
            val lease = success(f.worker { f.claim(seconds = 1) }).single()
            if (first == null) first = lease
            assertEquals(attempt, lease["attempts"])
            val until = (lease["lease_until"] as java.sql.Timestamp).toInstant()
            Thread.sleep(
                java.time.Duration.between(Instant.now(), until).toMillis().coerceAtLeast(0) + 30
            )
        }
        assertTrue(success(f.worker { f.claim() }).isEmpty())
        val failed = success(f.s.run { f.rows("collaboration_space_room_status") }).single()
        assertEquals("FAILED", failed["state"])
        assertEquals("ATTEMPTS_EXHAUSTED", failed["failure_code"])
        assertEquals(1, success(f.s.run { f.retry() }))
        val retried = success(f.s.run { f.rows("collaboration_space_room_status") }).single()
        assertEquals(1L, retried["revision"])
        assertEquals(0L, retried["last_outcome_revision"])
        assertEquals("ATTEMPTS_EXHAUSTED", retried["last_failure_code"])
        val current = success(f.worker { f.claim() }).single()
        assertInstanceOf(
            Result.Failed::class.java,
            f.worker(f.s.actor) { f.publish(requireNotNull(first)) },
        )
        success(
            f.worker(f.s.actor) {
                f.publish(current)
                f.finish(current)
            }
        )
        val completed = success(f.s.run { f.rows("collaboration_space_room_status") }).single()
        assertEquals("SUCCEEDED", completed["state"])
        assertEquals(1L, completed["last_outcome_revision"])
        assertNull(completed["last_failure_code"])
    }

    @Test
    fun requestExpiryIsRecheckedAfterDeviceWaitAndExpiredWorkHasATerminalOutcome() {
        val f = Fixture()
        success(f.s.run { f.intent() })
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val guard =
                pool.submit<Result<Int>> {
                    f.s.run {
                        f.s.runtime.queryForList(
                            "select id from collaboration_space_devices where id=? for update",
                            f.s.device,
                        )
                        held.countDown()
                        assertTrue(release.await(10, TimeUnit.SECONDS))
                        1
                    }
                }
            assertTrue(held.await(10, TimeUnit.SECONDS))
            val pending = pool.submit<Result<Int>> { f.s.run { f.enqueue(lifetimeSeconds = 1) } }
            var blocked = false
            repeat(100) {
                if (!blocked) {
                    blocked =
                        f.s.owner.queryForObject(
                            "select exists(select 1 from pg_stat_activity where wait_event_type='Lock' and query like 'insert into collaboration_space_room_provisioning%')",
                            Boolean::class.java,
                        ) == true
                    if (!blocked) TimeUnit.MILLISECONDS.sleep(10)
                }
            }
            assertTrue(blocked)
            TimeUnit.MILLISECONDS.sleep(1200)
            release.countDown()
            success(guard.get(10, TimeUnit.SECONDS))
            assertInstanceOf(Result.Failed::class.java, pending.get(10, TimeUnit.SECONDS))
            assertTrue(
                success(f.s.run { f.rows("collaboration_space_room_provisioning") }).isEmpty()
            )
            success(f.s.run { f.enqueue(lifetimeSeconds = 1) })
            val expiry =
                (success(f.s.run { f.rows("collaboration_space_room_status") })
                        .single()["expires_at"]
                        as java.sql.Timestamp)
                    .toInstant()
            Thread.sleep(
                java.time.Duration.between(Instant.now(), expiry).toMillis().coerceAtLeast(0) + 30
            )
            assertTrue(success(f.worker { f.claim() }).isEmpty())
            val status = success(f.s.run { f.rows("collaboration_space_room_status") }).single()
            assertEquals("FAILED", status["state"])
            assertEquals("REQUEST_EXPIRED", status["failure_code"])
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private inner class Fixture {
        val s = SpaceGatewayMigrationFixture(postgres).also { it.migrate() }
        val peer = UUID.randomUUID()
        val conversation = UUID.randomUUID()
        val pair = listOf(s.account, peer).sortedBy(UUID::toString)
        val worker = JdbcTemplate(s.workerSource)
        val workerTransactions =
            PostgresTransactionRunner(JdbcTransactionManager(s.workerSource), worker)

        init {
            Flyway.configure().dataSource(s.source).target("131").load().migrate()
            s.owner.execute(
                "grant select,insert,update,delete on all tables in schema public to space_gateway_fixture,space_gateway_worker"
            )
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
                s.device,
                deviceVersion,
                generation,
                s.account,
                s.parent,
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

        fun publish(lease: Map<String, Any?>, room: String = "!original") =
            worker.update(
                "insert into collaboration_space_room_bindings(space_id,conversation_id,published_by,request_revision,lease_token,matrix_room_id,created_at) values(?,?,?,?,?,?,?::timestamptz)",
                s.space,
                conversation,
                s.account,
                lease["revision"],
                lease["lease_token"],
                room,
                now().toString(),
            )

        fun finish(lease: Map<String, Any?>) =
            worker.update(
                "update collaboration_space_room_provisioning set state='SUCCEEDED',completed_at=statement_timestamp(),last_outcome_revision=revision,last_outcome_state='SUCCEEDED',last_outcome_at=statement_timestamp(),failure_code=null,last_failure_code=null,lease_owner=null,lease_token=null,lease_until=null where space_id=? and conversation_id=? and lease_token=?",
                s.space,
                conversation,
                lease["lease_token"],
            )

        fun cancel() =
            s.runtime.update(
                "update collaboration_space_room_provisioning set state='CANCELLED',completed_at=now(),last_outcome_revision=revision,last_outcome_state='CANCELLED',last_outcome_at=now(),lease_owner=null,lease_token=null,lease_until=null where space_id=? and conversation_id=?",
                s.space,
                conversation,
            )

        fun retry() =
            s.runtime.update(
                "update collaboration_space_room_provisioning set revision=revision+1,state='QUEUED',attempts=0,completed_at=null,failure_code=null,requested_at=now(),available_at=now(),expires_at=now()+interval '30 seconds' where space_id=? and conversation_id=?",
                s.space,
                conversation,
            )
    }

    private fun <T> success(value: Result<T>): T = (value as Result.Success).value
}
