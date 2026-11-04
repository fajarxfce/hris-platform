package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationLegacyRuntimeCompletionMigrationTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun completionCapabilityHasNoPublicOrApiExecutionAndCannotReadSecrets() {
        with(Fixture()) {
            migrate()
            val signature =
                "complete_collaboration_legacy_runtime(uuid,uuid,uuid,bigint,bigint,bigint,bigint,bigint,bigint,bigint,text,timestamptz)"
            assertFalse(
                sql.queryForObject(
                    "select has_function_privilege('legacy_completion_fixture',?,'EXECUTE')",
                    Boolean::class.java,
                    signature,
                )!!
            )
            assertTrue(
                sql.queryForObject(
                    "select has_function_privilege('legacy_completion_worker',?,'EXECUTE')",
                    Boolean::class.java,
                    signature,
                )!!
            )
            val role =
                sql.queryForMap(
                    "select rolcanlogin,rolinherit,rolsuper,rolcreatedb,rolcreaterole,rolbypassrls from pg_roles where rolname='hris_collaboration_transfer_owner'"
                )
            assertTrue(role.values.all { it == false })
            assertFalse(
                sql.queryForObject(
                    "select pg_has_role('legacy_completion_fixture','hris_collaboration_transfer_owner','MEMBER')",
                    Boolean::class.java,
                )!!
            )
            assertFalse(
                sql.queryForObject(
                    "select pg_has_role('legacy_completion_worker','hris_collaboration_transfer_owner','MEMBER')",
                    Boolean::class.java,
                )!!
            )
            assertEquals(
                "hris_collaboration_transfer_owner",
                sql.queryForObject(
                    "select pg_get_userbyid(proowner) from pg_proc where oid=?::regprocedure",
                    String::class.java,
                    signature,
                ),
            )
            assertTrue(
                sql.queryForObject(
                    "select prosecdef and proconfig=array['search_path=pg_catalog, public'] from pg_proc where oid=?::regprocedure",
                    Boolean::class.java,
                    signature,
                )!!
            )
            for ((table, column) in
                listOf(
                    "accounts" to "email",
                    "native_sessions" to "refresh_hash",
                    "collaboration_provider_sessions" to "token_encrypted",
                    "collaboration_gateway_sessions" to "access_hash",
                    "collaboration_space_provider_sessions" to "token_encrypted",
                )) assertFalse(
                sql.queryForObject(
                    "select has_column_privilege('hris_collaboration_transfer_owner',?,?,'SELECT')",
                    Boolean::class.java,
                    table,
                    column,
                )!!
            )
            prepare()
            assertInstanceOf(Result.Failed::class.java, run { complete(sql = runtime) })
            assertInstanceOf(
                Result.Failed::class.java,
                runWorker(actor.copy(companyId = company)) { complete() },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                runWorker(actor.copy(accountId = peer)) { complete() },
            )
            assertTrue(completions().isEmpty())
            assertEquals(
                "ACTIVE",
                sql.queryForObject(
                    "select state from collaboration_devices where id=?",
                    String::class.java,
                    legacyDevice,
                ),
            )
        }
    }

    @Test
    fun retirementAndGroupPublicationRetainOneImmutableMappingThroughParentPurgeAndRestore() {
        with(Fixture()) {
            migrate()
            prepare()
            val originalGateway =
                sql.queryForMap(
                    "select * from collaboration_gateway_sessions where id=?",
                    gatewayId,
                )
            val originalProvider =
                sql.queryForMap(
                    "select * from collaboration_provider_sessions where device_id=?",
                    legacyDevice,
                )
            assertTrue(
                success(
                    runWorker {
                        publish()
                        complete()
                    }
                )
            )
            val mapping = completions().single()
            assertEquals(legacyDevice, mapping["legacy_device_id"])
            assertEquals(targetId, mapping["device_id"])
            assertEquals(fingerprint, mapping["fingerprint"])
            assertEquals(2L, mapping["source_device_version"])
            assertEquals(1L, mapping["source_provider_generation"])
            assertEquals(gatewayId, mapping["source_gateway_id"])
            assertEquals(1L, mapping["source_gateway_version"])
            val gateway =
                sql.queryForMap(
                    "select * from collaboration_gateway_sessions where id=?",
                    gatewayId,
                )
            for (column in
                listOf(
                    "access_hash",
                    "refresh_hash",
                    "replay_encrypted",
                    "previous_refresh_hash",
                    "bootstrap_operation_id",
                    "replay_kind",
                    "replay_until",
                )) assertNull(gateway[column])
            for (column in
                listOf(
                    "created_at",
                    "rotated_at",
                    "expires_at",
                    "access_expires_at",
                    "client_build",
                    "native_session_id",
                )) assertEquals(originalGateway[column], gateway[column])
            val provider =
                sql.queryForMap(
                    "select * from collaboration_provider_sessions where device_id=?",
                    legacyDevice,
                )
            assertNull(provider["token_encrypted"])
            for (column in
                listOf(
                    "matrix_user_id",
                    "matrix_device_id",
                    "created_at",
                    "token_expires_at",
                )) assertEquals(originalProvider[column], provider[column])
            assertEquals(
                "ACTIVE",
                sql.queryForObject(
                    "select state from collaboration_space_devices where id=?",
                    String::class.java,
                    targetId,
                ),
            )
            assertEquals(
                "SUCCEEDED",
                sql.queryForObject(
                    "select state from collaboration_space_provisioning where device_id=?",
                    String::class.java,
                    targetId,
                ),
            )
            assertEquals("DRAINING", heads().single()["phase"])
            assertEquals(listOf(mapping), completions(actor.copy(companyId = employer)))
            assertTrue(completions(actor.copy(accountId = peer)).isEmpty())
            assertInstanceOf(
                Result.Failed::class.java,
                run {
                    runtime.update(
                        "insert into collaboration_legacy_runtime_completions select * from collaboration_legacy_runtime_completions"
                    )
                },
            )
            for (statement in
                listOf(
                    "update collaboration_legacy_runtime_completions set completed_at=completed_at+interval '1 second'",
                    "delete from collaboration_legacy_runtime_completions",
                )) {
                assertEquals(0, success(run { runtime.update(statement) }))
                assertThrows(org.springframework.dao.DataAccessException::class.java) {
                    sql.execute(statement)
                }
            }
            assertInstanceOf(Result.Failed::class.java, runWorker { complete() })
            sql.update(
                "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
                parent,
            )
            sql.update("delete from native_sessions where id=?", parent)
            assertEquals(listOf(mapping), completions())
            val restored = restore()
            assertEquals(
                listOf(mapping),
                restored.queryForList("select * from collaboration_legacy_runtime_completions"),
            )
            assertEquals(
                2,
                restored.queryForObject(
                    "select count(*) from pg_class where relname in ('collaboration_legacy_runtime','collaboration_legacy_runtime_completions') and relrowsecurity and relforcerowsecurity",
                    Int::class.java,
                ),
            )
            assertEquals(
                "124",
                restored.queryForObject(
                    "select version from flyway_schema_history where success order by installed_rank desc limit 1",
                    String::class.java,
                ),
            )
        }
    }

    @Test
    fun callerFailureCancellationAndInterruptionRollBackBothOldAndNewAuthorities() {
        with(Fixture()) {
            migrate()
            prepare(withGateway = false)
            val original = snapshot()
            val cancel = CancellationException("Owned transfer cancellation")
            val interrupt = InterruptedException("Owned transfer interruption")
            for (mode in listOf("failure", "cancel", "interrupt", "flag")) {
                fun run(): Result<Unit> =
                    workerTransactions.run(actor) {
                        publish()
                        assertTrue(complete())
                        when (mode) {
                            "cancel" -> throw cancel
                            "interrupt" -> throw interrupt
                            "flag" -> {
                                Thread.currentThread().interrupt()
                                Result.Success(Unit)
                            }
                            else ->
                                Result.Failed(
                                    Failure(FailureKind.UNAVAILABLE, "fixture_after_transfer")
                                )
                        }
                    }
                when (mode) {
                    "cancel" ->
                        assertSame(
                            cancel,
                            assertThrows(CancellationException::class.java) { run() },
                        )
                    "interrupt" ->
                        assertSame(
                            interrupt,
                            assertThrows(InterruptedException::class.java) { run() },
                        )
                    "flag" ->
                        try {
                            assertThrows(InterruptedException::class.java) { run() }
                            assertTrue(Thread.currentThread().isInterrupted)
                        } finally {
                            Thread.interrupted()
                        }
                    else -> assertInstanceOf(Result.Failed::class.java, run())
                }
                assertEquals(original, snapshot())
            }
            assertTrue(
                success(
                    runWorker {
                        publish()
                        complete()
                    }
                )
            )
            assertNull(completions().single()["source_gateway_id"])
            assertNull(completions().single()["source_gateway_version"])
        }
    }

    @Test
    fun substitutedVersionsTargetsKeyProofAndNativeAdmissionNeverPartiallyRetire() {
        with(Fixture()) {
            migrate()
            prepare()
            val original = snapshot()
            val variants: List<() -> Boolean> =
                listOf(
                    { complete(requestedSpace = UUID.randomUUID()) },
                    { complete(target = UUID.randomUUID()) },
                    { complete(native = UUID.randomUUID()) },
                    { complete(nativeVersion = 1) },
                    { complete(targetVersion = 2) },
                    { complete(targetVersion = 1000000) },
                    { complete(targetGeneration = 1) },
                    { complete(queueRevision = 1) },
                    { complete(oldVersion = 2) },
                    { complete(oldGeneration = 1) },
                    { complete(gatewayVersion = 1) },
                    { complete(gatewayVersion = null) },
                    { complete(key = "b".repeat(64)) },
                    { complete(key = "a".repeat(63)) },
                    { complete(observed = at.minusSeconds(1)) },
                    { complete(observed = now().plusSeconds(1)) },
                )
            for (variant in variants) {
                assertInstanceOf(
                    Result.Failed::class.java,
                    runWorker {
                        publish()
                        variant()
                    },
                )
                assertEquals(original, snapshot())
            }
            assertTrue(
                success(
                    runWorker {
                        publish()
                        complete()
                    }
                )
            )
        }
    }

    @Test
    fun liveOriginalLeaseBlocksCompletionUntilActualExpiry() {
        with(Fixture()) {
            migrate()
            prepare(leaseSeconds = 2)
            val original = snapshot()
            assertInstanceOf(
                Result.Failed::class.java,
                runWorker {
                    publish()
                    complete()
                },
            )
            assertEquals(original, snapshot())
            val remaining =
                java.time.Duration.between(now(), requireNotNull(providerLeaseUntil))
                    .toMillis()
                    .coerceAtLeast(0) + 20
            assertTrue(remaining <= 2020)
            Thread.sleep(remaining)
            assertTrue(
                success(
                    runWorker {
                        publish()
                        complete()
                    }
                )
            )
            assertEquals(1, leases().size)
            assertNull(leases().single()["released_at"])
            assertEquals(1, completions().size)
        }
    }

    @Test
    fun databaseTimeIsRecheckedAfterWaitingForTheFrozenRuntimeGuard() {
        with(Fixture()) {
            migrate()
            prepare()
            sql.update(
                "update native_sessions set access_expires_at=clock_timestamp()+interval '1 second' where id=?",
                parent,
            )
            val original = snapshot()
            val held = CountDownLatch(1)
            val release = CountDownLatch(1)
            val entered = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val blocker =
                    pool.submit<Result<Unit>> {
                        run {
                            runtime.queryForList(
                                "select device_id from collaboration_legacy_runtime where company_id=? and device_id=? for update",
                                company,
                                legacyDevice,
                            )
                            held.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                    }
                assertTrue(held.await(5, TimeUnit.SECONDS))
                val completion =
                    pool.submit<Result<Boolean>> {
                        runWorker {
                            publish()
                            entered.countDown()
                            complete()
                        }
                    }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                var waiting = false
                while (System.nanoTime() < deadline && !waiting) {
                    waiting =
                        sql.queryForObject(
                            "select count(*) from pg_stat_activity where usename='legacy_completion_worker' and wait_event_type='Lock'",
                            Int::class.java,
                        )!! > 0
                    if (!waiting) Thread.sleep(10)
                }
                assertTrue(waiting)
                Thread.sleep(1100)
                release.countDown()
                success(blocker.get(5, TimeUnit.SECONDS))
                assertInstanceOf(Result.Failed::class.java, completion.get(5, TimeUnit.SECONDS))
                assertEquals(original, snapshot())
            } finally {
                release.countDown()
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun gatewayMetadataMigrationPreservesOriginalHeadsAndExposesOnlyOwnedPublicFields() {
        with(Fixture()) {
            migrate()
            prepare()
            val before = sql.queryForList("select * from collaboration_gateway_sessions")
            enableGatewayMetadata()
            assertEquals(before, sql.queryForList("select * from collaboration_gateway_sessions"))
            val metadata = gatewayHeads().single()
            assertEquals(
                setOf(
                    "space_id",
                    "account_id",
                    "legacy_company_id",
                    "legacy_device_id",
                    "gateway_id",
                    "native_session_id",
                    "version",
                    "created_at",
                    "rotated_at",
                    "revoked_at",
                ),
                metadata.keys,
            )
            assertEquals(space, metadata["space_id"])
            assertEquals(account, metadata["account_id"])
            assertEquals(company, metadata["legacy_company_id"])
            assertEquals(legacyDevice, metadata["legacy_device_id"])
            assertEquals(gatewayId, metadata["gateway_id"])
            assertEquals(parent, metadata["native_session_id"])
            assertEquals(0L, metadata["version"])
            assertEquals(listOf(metadata), gatewayHeads(actor.copy(companyId = employer)))
            assertTrue(gatewayHeads(actor.copy(accountId = peer)).isEmpty())
            assertTrue(
                success(
                        run { runtime.queryForList("select * from collaboration_gateway_sessions") }
                    )
                    .isEmpty()
            )
            assertEquals(
                "hris_collaboration_legacy_reader",
                sql.queryForObject(
                    "select pg_get_userbyid(relowner) from pg_class where oid='collaboration_space_legacy_gateways'::regclass",
                    String::class.java,
                ),
            )
            assertTrue(
                sql.queryForObject(
                    "select 'security_barrier=true'=any(reloptions) from pg_class where oid='collaboration_space_legacy_gateways'::regclass",
                    Boolean::class.java,
                )!!
            )
            assertFalse(
                sql.queryForObject(
                    "select pg_has_role('legacy_completion_fixture','hris_collaboration_legacy_reader','MEMBER')",
                    Boolean::class.java,
                )!!
            )
            for (column in
                listOf(
                    "access_hash",
                    "refresh_hash",
                    "previous_refresh_hash",
                    "bootstrap_operation_id",
                    "replay_encrypted",
                    "replay_kind",
                    "replay_until",
                )) assertFalse(
                sql.queryForObject(
                    "select has_column_privilege('hris_collaboration_legacy_reader','collaboration_gateway_sessions',?,'SELECT')",
                    Boolean::class.java,
                    column,
                )!!
            )
        }
    }

    @Test
    fun gatewayMetadataVisibilityTracksGroupEligibilityWithoutGrantingCompanyResources() {
        with(Fixture()) {
            migrate()
            prepare()
            enableGatewayMetadata()
            assertEquals(1, gatewayHeads().size)
            assertTrue(
                success(
                        run {
                            runtime.queryForList("select * from collaboration_provider_sessions")
                        }
                    )
                    .isEmpty()
            )
            sql.update(
                "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                employer,
                account,
            )
            assertTrue(gatewayHeads().isEmpty())
            sql.update(
                "update company_memberships set active=true,version=version+1 where company_id=? and account_id=?",
                employer,
                account,
            )
            assertEquals(1, gatewayHeads().size)
            sql.update("update accounts set active=false where id=?", account)
            assertTrue(gatewayHeads().isEmpty())
            assertEquals(
                1,
                sql.queryForObject(
                    "select count(*) from collaboration_gateway_sessions",
                    Int::class.java,
                ),
            )
        }
    }

    @Test
    fun gatewayMetadataKeepsRetiredParentProvenanceAndPrivacyAfterArchiveRestore() {
        with(Fixture()) {
            migrate()
            prepare()
            enableGatewayMetadata()
            val live = gatewayHeads().single()
            assertTrue(
                success(
                    runWorker {
                        publish()
                        complete()
                    }
                )
            )
            val retired = gatewayHeads().single()
            assertEquals(1L, retired["version"])
            assertNotNull(retired["revoked_at"])
            for (column in
                listOf("gateway_id", "native_session_id", "created_at", "rotated_at")) assertEquals(
                live[column],
                retired[column],
            )
            sql.update(
                "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
                parent,
            )
            sql.update("delete from native_sessions where id=?", parent)
            assertEquals(listOf(retired), gatewayHeads())
            val restored = restore()
            val restoredSource = requireNotNull(restored.dataSource)
            val restoredTransactions =
                PostgresTransactionRunner(JdbcTransactionManager(restoredSource), restored)
            assertEquals(
                listOf(retired),
                success(
                    restoredTransactions.run(actor) {
                        safeDatabaseCall {
                            restored.queryForList(
                                "select * from collaboration_space_legacy_gateways"
                            )
                        }
                    }
                ),
            )
            assertEquals(
                "hris_collaboration_legacy_reader",
                restored.queryForObject(
                    "select pg_get_userbyid(relowner) from pg_class where oid='collaboration_space_legacy_gateways'::regclass",
                    String::class.java,
                ),
            )
            assertFalse(
                restored.queryForObject(
                    "select has_column_privilege('hris_collaboration_legacy_reader','collaboration_gateway_sessions','replay_encrypted','SELECT')",
                    Boolean::class.java,
                )!!
            )
            assertEquals(
                "125",
                restored.queryForObject(
                    "select version from flyway_schema_history where success order by installed_rank desc limit 1",
                    String::class.java,
                ),
            )
        }
    }

    @Test
    fun completedRetainedDeviceKeepsOriginalRecipientIdentityAcrossCompanyScope() {
        val f = Fixture()
        f.migrate()
        Flyway.configure().dataSource(f.source).target("135").load().migrate()
        f.sql.execute(
            "grant select,insert,update,delete on all tables in schema public to legacy_completion_fixture,legacy_completion_worker"
        )
        f.prepare()
        val target = requireNotNull(f.targetId)
        fun reserve(sql: JdbcTemplate) =
            sql.update(
                "insert into collaboration_space_key_publications(space_id,account_id,device_id,native_session_id,matrix_user_id,matrix_device_id,fingerprint,version,reserved_at) values(?,?,?,?,?,?,?,0,statement_timestamp())",
                f.space,
                f.account,
                target,
                f.parent,
                f.legacyUser,
                "HRIS_${compact(f.legacyDevice)}",
                f.fingerprint,
            )
        val before = f.snapshot()
        assertInstanceOf(
            Result.Failed::class.java,
            f.runWorker {
                f.publish()
                reserve(
                    f.worker
                ) // An ACTIVE target without completed transfer cannot publish keys.
            },
        )
        assertEquals(before, f.snapshot())
        assertTrue(
            f.sql.queryForList("select * from collaboration_space_key_publications").isEmpty()
        )
        assertTrue(
            success(
                f.runWorker {
                    f.publish()
                    f.complete()
                }
            )
        )
        success(f.run { reserve(f.runtime) })
        val original = f.sql.queryForList("select * from collaboration_device_key_publications")
        val selected = f.sql.queryForList("select * from collaboration_space_key_publications")
        assertNotEquals(f.legacyDevice, target)

        // The peer has a new organization identity; receiving retains the other
        // participant's original company identity without access to its HR resources.
        val peerUser = "@hrisg_${compact(f.space)}_${compact(f.peer)}:matrix.example.test"
        success(
            f.run(f.actor.copy(accountId = f.peer)) {
                f.runtime.update(
                    "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,created_at) values(?,?,'matrix.example.test',?,'SPACE',statement_timestamp())",
                    f.space,
                    f.peer,
                    peerUser,
                )
            }
        )
        val pair = listOf(f.account, f.peer).sortedBy(UUID::toString)
        val conversation = UUID.randomUUID()
        fun user(id: UUID) = if (id == f.account) f.legacyUser else peerUser
        success(
            f.run {
                f.runtime.update(
                    "insert into collaboration_space_direct_conversations(space_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,statement_timestamp())",
                    f.space,
                    conversation,
                    pair[0],
                    pair[1],
                    f.account,
                )
                f.runtime.update(
                    "insert into collaboration_space_room_intents(space_id,conversation_id,first_account_id,second_account_id,requested_by,matrix_creator_account_id,matrix_server_name,first_matrix_user_id,second_matrix_user_id,origin,created_at) values(?,?,?,?,?,?,'matrix.example.test',?,?,'SPACE',statement_timestamp())",
                    f.space,
                    conversation,
                    pair[0],
                    pair[1],
                    f.account,
                    f.account,
                    user(pair[0]),
                    user(pair[1]),
                )
                f.runtime.update(
                    "insert into collaboration_space_room_provisioning(space_id,conversation_id,device_id,device_version,provider_generation,account_id,native_session_id,revision,native_session_version,credential_version,authenticated_at,correlation_id,client_platform,client_build,created_at,requested_at,expires_at,available_at,state,attempts) values(?,?,?,1,0,?,?,0,0,0,?::timestamptz,?,'ANDROID',28,statement_timestamp(),statement_timestamp(),statement_timestamp()+interval '60 seconds',statement_timestamp(),'QUEUED',0)",
                    f.space,
                    conversation,
                    target,
                    f.account,
                    f.parent,
                    f.actor.authenticatedAt.toString(),
                    UUID.randomUUID(),
                )
            }
        )
        fun candidates(actor: Actor = f.actor) =
            success(
                f.run(actor) {
                    f.runtime.queryForList(
                        "select * from collaboration_space_recipient_devices order by account_id,device_id"
                    )
                }
            )
        assertTrue(candidates(f.actor.copy(accountId = f.peer)).isEmpty())
        val lease =
            success(
                f.runWorker {
                    f.worker
                        .queryForList(
                            "select * from claim_collaboration_space_room_provisioning(?,1,30)",
                            UUID.randomUUID(),
                        )
                        .single()
                }
            )
        success(
            f.runWorker {
                f.worker.update(
                    "insert into collaboration_space_room_bindings(space_id,conversation_id,published_by,request_revision,lease_token,matrix_room_id,created_at,matrix_creator_account_id) values(?,?,?,?,?,'!retained-recipient',statement_timestamp(),?)",
                    f.space,
                    conversation,
                    f.account,
                    lease["revision"],
                    lease["lease_token"],
                    f.account,
                )
                f.worker.update(
                    "update collaboration_space_room_provisioning set state='SUCCEEDED',completed_at=statement_timestamp(),last_outcome_revision=revision,last_outcome_state='SUCCEEDED',last_outcome_at=statement_timestamp(),failure_code=null,last_failure_code=null,lease_owner=null,lease_token=null,lease_until=null where space_id=? and conversation_id=? and lease_token=?",
                    f.space,
                    conversation,
                    lease["lease_token"],
                )
            }
        )
        val recipient = candidates(f.actor.copy(accountId = f.peer)).single()
        assertEquals(target, recipient["device_id"])
        assertEquals(f.legacyUser, recipient["matrix_user_id"])
        assertEquals("HRIS_${compact(f.legacyDevice)}", recipient["matrix_device_id"])
        assertEquals(f.fingerprint, recipient["fingerprint"])
        assertEquals(false, recipient["publication_confirmed"])
        f.sql.update("update companies set active=false,version=version+1 where id=?", f.company)
        f.sql.update(
            "update native_sessions set access_expires_at=clock_timestamp()-interval '1 second' where id=?",
            f.parent,
        )
        assertEquals(recipient, candidates(f.actor.copy(accountId = f.peer)).single())
        assertTrue(candidates(f.actor.copy(accountId = f.peer, companyId = f.company)).isEmpty())
        assertTrue(
            success(
                    f.run(f.actor.copy(accountId = f.peer)) {
                        f.runtime.queryForList("select * from collaboration_space_key_publications")
                    }
                )
                .isEmpty()
        )
        f.sql.update("delete from native_sessions where id=?", f.parent)
        assertTrue(candidates(f.actor.copy(accountId = f.peer)).isEmpty())
        assertEquals(
            original,
            f.sql.queryForList("select * from collaboration_device_key_publications"),
        )
        assertEquals(
            selected,
            f.sql.queryForList("select * from collaboration_space_key_publications"),
        )
        val restored = f.restore()
        assertEquals(
            original,
            restored.queryForList("select * from collaboration_device_key_publications"),
        )
        assertEquals(
            selected,
            restored.queryForList("select * from collaboration_space_key_publications"),
        )
    }

    @Test
    fun retainedGroupPushCapabilityRequiresCompletedHandoverAndPreservesCompanyCapability() {
        val f = Fixture()
        f.migrate()
        Flyway.configure().dataSource(f.source).target("136").load().migrate()
        f.sql.execute(
            "grant select,insert,update,delete on all tables in schema public to legacy_completion_fixture,legacy_completion_worker"
        )
        success(
            f.run(f.actor.copy(companyId = f.company)) {
                f.runtime.update(
                    "insert into collaboration_push_bindings(company_id,account_id,device_id,native_session_id,key_hash,key_encrypted,version,reserved_at) values(?,?,?,?,'${"c".repeat(64)}','original-push-fixture',0,clock_timestamp())",
                    f.company,
                    f.account,
                    f.legacyDevice,
                    f.parent,
                )
            }
        )
        val original = f.sql.queryForList("select * from collaboration_push_bindings")
        f.prepare()
        val target = requireNotNull(f.targetId)
        fun reserve(sql: JdbcTemplate) =
            sql.update(
                "insert into collaboration_space_push_bindings(space_id,account_id,device_id,native_session_id,matrix_user_id,matrix_device_id,key_hash,key_encrypted,version,reserved_at) values(?,?,?,?,?,?,'${"d".repeat(64)}','group-push-fixture',0,clock_timestamp())",
                f.space,
                f.account,
                target,
                f.parent,
                f.legacyUser,
                "HRIS_${compact(f.legacyDevice)}",
            )
        val before = f.snapshot()
        assertInstanceOf(
            Result.Failed::class.java,
            f.runWorker {
                f.publish()
                reserve(f.worker)
            },
        )
        assertEquals(before, f.snapshot())
        assertTrue(f.sql.queryForList("select * from collaboration_space_push_bindings").isEmpty())
        assertTrue(
            success(
                f.runWorker {
                    f.publish()
                    f.complete()
                }
            )
        )
        assertEquals(1, success(f.run { reserve(f.runtime) }))
        val binding = f.sql.queryForList("select * from collaboration_space_push_bindings").single()
        assertEquals(f.legacyUser, binding["matrix_user_id"])
        assertEquals("HRIS_${compact(f.legacyDevice)}", binding["matrix_device_id"])
        assertEquals(target, binding["device_id"])
        assertNotEquals(f.legacyDevice, target)
        assertEquals(original, f.sql.queryForList("select * from collaboration_push_bindings"))
        assertEquals(
            1,
            success(
                f.run {
                    f.runtime.update(
                        "update collaboration_space_push_bindings set version=1,published_at=clock_timestamp() where device_id=?",
                        target,
                    )
                }
            ),
        )
        val retained = f.sql.queryForList("select * from collaboration_space_push_bindings")
        f.sql.update("delete from native_sessions where id=?", f.parent)
        assertEquals(
            retained,
            success(
                f.run { f.runtime.queryForList("select * from collaboration_space_push_bindings") }
            ),
        )
        assertEquals(original, f.sql.queryForList("select * from collaboration_push_bindings"))
        val restored = f.restore()
        assertEquals(
            retained,
            restored.queryForList("select * from collaboration_space_push_bindings"),
        )
        assertEquals(original, restored.queryForList("select * from collaboration_push_bindings"))
    }

    private inner class Fixture {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val sql = JdbcTemplate(source)
        val runtimeSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "legacy_completion_fixture",
                "isolated-fixture-only",
            )
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val workerSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "legacy_completion_worker",
                "isolated-fixture-only",
            )
        val worker = JdbcTemplate(workerSource)
        val workerTransactions =
            PostgresTransactionRunner(JdbcTransactionManager(workerSource), worker)
        var gatewayId: UUID? = null
        var targetId: UUID? = null
        var claimed: Map<String, Any?>? = null
        var keysObservedAt: Instant? = null
        var providerLeaseUntil: Instant? = null
        val company = UUID.randomUUID()
        val employer = UUID.randomUUID()
        val account = UUID.randomUUID()
        val peer = UUID.randomUUID()
        val legacyDevice = UUID.randomUUID()
        val at = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val actor =
            Actor(
                account,
                null,
                emptySet(),
                at.minusSeconds(120),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val space: UUID
        val parent: UUID
        val fingerprint = "a".repeat(64)
        val legacyUser = "@hris_${compact(company)}_${compact(account)}:matrix.example.test"

        init {
            Flyway.configure().dataSource(source).target("119").load().migrate()
            space =
                requireNotNull(
                    sql.queryForObject("select id from collaboration_spaces", UUID::class.java)
                )
            for ((index, id) in listOf(company, employer).withIndex()) sql.update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                id,
                "SPACEDEV$index",
                "Fictional company $index",
            )
            for ((index, id) in listOf(account, peer).withIndex()) {
                sql.update(
                    "insert into accounts(id,email,display_name) values(?,?,?)",
                    id,
                    "space-device-$index@example.test",
                    "Fictional employee $index",
                )
                sql.update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    employer,
                    id,
                )
                sql.update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                    employer,
                    id,
                )
            }
            sql.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
            parent = addParent()
            sql.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                company,
                legacyDevice,
                account,
                at.minusSeconds(60).toString(),
                at.minusSeconds(60).toString(),
            )
            sql.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                company,
                legacyDevice,
                account,
                parent,
                at.minusSeconds(30).toString(),
            )
            sql.update(
                "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'fixture-envelope-not-a-real-token',?::timestamptz,?::timestamptz,?::timestamptz)",
                company,
                legacyDevice,
                account,
                legacyUser,
                "HRIS_${compact(legacyDevice)}",
                at.minusSeconds(30).toString(),
                at.minusSeconds(30).toString(),
                at.plusSeconds(270).toString(),
            )
            sql.update(
                "update collaboration_devices set state='ACTIVE',version=1,updated_at=?::timestamptz where company_id=? and id=?",
                at.minusSeconds(30).toString(),
                company,
                legacyDevice,
            )
            sql.update(
                "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?,?,0,?::timestamptz)",
                company,
                legacyDevice,
                account,
                fingerprint,
                at.minusSeconds(20).toString(),
            )
            sql.update(
                "update collaboration_device_key_publications set version=1,published_at=?::timestamptz where company_id=? and device_id=?",
                at.minusSeconds(19).toString(),
                company,
                legacyDevice,
            )
            sql.update(
                "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,legacy_company_id,legacy_device_id,created_at) values(?,?,'matrix.example.test',?,'LEGACY',?,?,?::timestamptz)",
                space,
                account,
                legacyUser,
                company,
                legacyDevice,
                at.minusSeconds(10).toString(),
            )
        }

        fun addParent(owner: UUID = account): UUID =
            UUID.randomUUID().also {
                sql.update(
                    "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
                    it,
                    owner,
                    compact(it).repeat(2),
                    actor.authenticatedAt.toString(),
                    at.plusSeconds(3600).toString(),
                    at.plusSeconds(900).toString(),
                    at.minusSeconds(60).toString(),
                    at.minusSeconds(30).toString(),
                )
            }

        fun migrate() {
            Flyway.configure().dataSource(source).target("124").load().migrate()
            sql.execute(
                "create role legacy_completion_fixture login password 'isolated-fixture-only'"
            )
            sql.execute("grant usage on schema public to legacy_completion_fixture")
            sql.execute(
                "create role legacy_completion_worker login password 'isolated-fixture-only'"
            )
            sql.execute("grant hris_worker_capability to legacy_completion_worker")
            sql.execute("grant usage on schema public to legacy_completion_worker")
            sql.execute(
                "grant select,insert,update,delete on all tables in schema public to legacy_completion_worker"
            )
            sql.execute(
                "grant select,insert,update,delete on all tables in schema public to legacy_completion_fixture"
            )
        }

        fun <T> run(current: Actor = actor, body: () -> T): Result<T> =
            transactions.run(current) { safeDatabaseCall(body) }

        fun insert(
            id: UUID,
            origin: String = "LEGACY",
            selectedSpace: UUID = space,
            user: String = legacyUser,
            oldDevice: UUID = legacyDevice,
            key: String = fingerprint,
            session: UUID = parent,
            platform: String = "ANDROID",
            providerDevice: String =
                if (origin == "LEGACY") "HRIS_${compact(oldDevice)}" else "HRISG_${compact(id)}",
        ): Int =
            runtime.update(
                "insert into collaboration_space_devices(space_id,id,account_id,matrix_user_id,matrix_device_id,origin,legacy_company_id,legacy_device_id,legacy_key_fingerprint,origin_native_session_id,platform,label,state,version,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,'Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                selectedSpace,
                id,
                account,
                user,
                providerDevice,
                origin,
                if (origin == "LEGACY") company else null,
                if (origin == "LEGACY") oldDevice else null,
                if (origin == "LEGACY") key else null,
                session,
                platform,
                at.toString(),
                at.toString(),
            )

        fun bind(device: UUID, session: UUID = parent): Int =
            runtime.update(
                "insert into collaboration_space_device_sessions(space_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                space,
                device,
                account,
                session,
                at.toString(),
            )

        fun ensure(current: Actor = actor.copy(companyId = company)): Result<Int> =
            run(current) {
                runtime.update(
                    "insert into collaboration_legacy_runtime(company_id,device_id,account_id,native_session_id,phase,version,created_at) values(?,?,?,?,'LEGACY',0,clock_timestamp())",
                    company,
                    legacyDevice,
                    account,
                    parent,
                )
            }

        fun claim(
            until: Instant = now().plusSeconds(30),
            current: Actor = actor.copy(companyId = company),
        ): Result<List<Map<String, Any?>>> =
            run(current) {
                runtime.queryForList(
                    "select * from claim_collaboration_legacy_runtime(?,?,?,?,?::timestamptz)",
                    company,
                    legacyDevice,
                    account,
                    parent,
                    until.toString(),
                )
            }

        fun release(token: UUID, current: Actor = actor): Result<Int> =
            run(current) {
                runtime.update(
                    "update collaboration_legacy_runtime_leases set released_at=clock_timestamp() where company_id=? and device_id=? and token=? and released_at is null",
                    company,
                    legacyDevice,
                    token,
                )
            }

        fun freeze(target: UUID): Result<Int> = run {
            runtime.update(
                "update collaboration_legacy_runtime set phase='DRAINING',version=1,frozen_at=clock_timestamp(),target_space_id=?,target_device_id=? where company_id=? and device_id=?",
                space,
                target,
                company,
                legacyDevice,
            )
        }

        fun target(): UUID =
            UUID.randomUUID().also { id ->
                success(run { insert(id) })
                success(run { bind(id) })
            }

        fun heads(current: Actor = actor) =
            success(
                run(current) {
                    runtime.queryForList(
                        "select * from collaboration_legacy_runtime order by company_id,device_id"
                    )
                }
            )

        fun leases(current: Actor = actor) =
            success(
                run(current) {
                    runtime.queryForList(
                        "select * from collaboration_legacy_runtime_leases order by company_id,device_id,slot"
                    )
                }
            )

        fun <T> runWorker(current: Actor = actor, body: () -> T): Result<T> =
            workerTransactions.run(current) { safeDatabaseCall(body) }

        fun prepare(withGateway: Boolean = true, leaseSeconds: Long? = null) {
            if (withGateway) {
                val id = UUID.randomUUID()
                sql.update(
                    "insert into collaboration_gateway_sessions(company_id,id,account_id,device_id,native_session_id,platform,client_build,version,access_hash,refresh_hash,created_at,rotated_at,expires_at,access_expires_at,replay_kind,bootstrap_operation_id,replay_until,replay_encrypted) values(?,?,?,?,?,'ANDROID',28,0,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz,'BOOTSTRAP',?,?::timestamptz,'fictional-replay-envelope')",
                    company,
                    id,
                    account,
                    legacyDevice,
                    parent,
                    "a".repeat(64),
                    "b".repeat(64),
                    at.minusSeconds(10).toString(),
                    at.minusSeconds(10).toString(),
                    at.plusSeconds(900).toString(),
                    at.plusSeconds(290).toString(),
                    UUID.randomUUID(),
                    at.plusSeconds(20).toString(),
                )
                gatewayId = id
            }
            val target = target()
            targetId = target
            val requested = now()
            success(
                run {
                    runtime.update(
                        "insert into collaboration_space_provisioning(space_id,device_id,account_id,native_session_id,revision,native_session_version,credential_version,authenticated_at,correlation_id,client_platform,client_build,created_at,requested_at,expires_at,available_at,state,attempts) values(?,?,?,?,0,0,0,?::timestamptz,?,'ANDROID',28,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz,'QUEUED',0)",
                        space,
                        target,
                        account,
                        parent,
                        actor.authenticatedAt.toString(),
                        actor.correlationId,
                        requested.toString(),
                        requested.toString(),
                        requested.plusSeconds(60).toString(),
                        requested.toString(),
                    )
                }
            )
            success(ensure())
            if (leaseSeconds != null) {
                providerLeaseUntil = now().plusSeconds(leaseSeconds)
                assertEquals(1, success(claim(requireNotNull(providerLeaseUntil))).size)
            }
            success(freeze(target))
            claimed =
                success(
                    runWorker {
                        worker
                            .queryForList(
                                "select * from claim_collaboration_space_provisioning(?,1,45)",
                                UUID.randomUUID(),
                            )
                            .single()
                    }
                )
            keysObservedAt = now()
        }

        fun publish() {
            val target = requireNotNull(targetId)
            val work = requireNotNull(claimed)
            val current = now().toString()
            worker.update(
                "insert into collaboration_space_provider_sessions(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,?,0,'fictional-group-envelope',?::timestamptz,?::timestamptz,?::timestamptz+interval '5 minutes')",
                space,
                target,
                account,
                parent,
                legacyUser,
                "HRIS_${compact(legacyDevice)}",
                current,
                current,
                current,
            )
            assertEquals(
                1,
                worker.update(
                    "update collaboration_space_devices set state='ACTIVE',version=1,updated_at=?::timestamptz where space_id=? and id=? and version=0",
                    current,
                    space,
                    target,
                ),
            )
            assertEquals(
                1,
                worker.update(
                    "update collaboration_space_provisioning set state='SUCCEEDED',completed_at=?::timestamptz,last_outcome_revision=revision,last_outcome_state='SUCCEEDED',last_outcome_at=?::timestamptz,last_failure_code=null,lease_owner=null,lease_token=null,lease_until=null where space_id=? and device_id=? and revision=0 and state='RUNNING' and lease_owner=? and lease_token=? and lease_until>clock_timestamp()",
                    current,
                    current,
                    space,
                    target,
                    work["lease_owner"],
                    work["lease_token"],
                ),
            )
        }

        fun complete(
            sql: JdbcTemplate = worker,
            requestedSpace: UUID = space,
            target: UUID = requireNotNull(targetId),
            native: UUID = parent,
            nativeVersion: Long = 0,
            targetVersion: Long = 1,
            targetGeneration: Long = 0,
            queueRevision: Long = 0,
            oldVersion: Long = 1,
            oldGeneration: Long = 0,
            gatewayVersion: Long? = if (gatewayId == null) null else 0L,
            key: String = fingerprint,
            observed: Instant = requireNotNull(keysObservedAt),
        ): Boolean =
            requireNotNull(
                sql.queryForObject(
                    "select complete_collaboration_legacy_runtime(?,?,?,?::bigint,?::bigint,?::bigint,?::bigint,?::bigint,?::bigint,?::bigint,?,?::timestamptz)",
                    Boolean::class.java,
                    requestedSpace,
                    target,
                    native,
                    nativeVersion,
                    targetVersion,
                    targetGeneration,
                    queueRevision,
                    oldVersion,
                    oldGeneration,
                    gatewayVersion,
                    key,
                    observed.toString(),
                )
            )

        fun completions(current: Actor = actor): List<Map<String, Any?>> =
            success(
                run(current) {
                    runtime.queryForList(
                        "select * from collaboration_legacy_runtime_completions order by legacy_company_id,legacy_device_id"
                    )
                }
            )

        fun snapshot(): Map<String, List<Map<String, Any?>>> =
            listOf(
                    "collaboration_devices",
                    "collaboration_provider_sessions",
                    "collaboration_gateway_sessions",
                    "collaboration_space_devices",
                    "collaboration_space_provider_sessions",
                    "collaboration_space_provisioning",
                    "collaboration_legacy_runtime",
                    "collaboration_legacy_runtime_leases",
                    "collaboration_legacy_runtime_completions",
                )
                .associateWith { sql.queryForList("select * from $it") }

        fun enableGatewayMetadata() {
            Flyway.configure().dataSource(source).target("125").load().migrate()
            sql.execute(
                "grant select on collaboration_space_legacy_gateways to legacy_completion_fixture,legacy_completion_worker"
            )
        }

        fun gatewayHeads(current: Actor = actor): List<Map<String, Any?>> =
            success(
                run(current) {
                    runtime.queryForList("select * from collaboration_space_legacy_gateways")
                }
            )

        fun restore(): JdbcTemplate {
            fun command(vararg args: String) {
                val result = postgres.execInContainer(*args)
                assertEquals(0, result.exitCode, result.stderr)
            }
            command(
                "pg_dump",
                "-U",
                postgres.username,
                "-d",
                postgres.databaseName,
                "-Fc",
                "-f",
                "/tmp/legacy-completion.dump",
            )
            command("createdb", "-U", postgres.username, "legacy_completion_restored")
            command(
                "pg_restore",
                "-U",
                postgres.username,
                "-d",
                "legacy_completion_restored",
                "--single-transaction",
                "--exit-on-error",
                "/tmp/legacy-completion.dump",
            )
            return JdbcTemplate(
                DriverManagerDataSource(
                    postgres.jdbcUrl.substringBeforeLast('/') + "/legacy_completion_restored",
                    postgres.username,
                    postgres.password,
                )
            )
        }
    }

    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
