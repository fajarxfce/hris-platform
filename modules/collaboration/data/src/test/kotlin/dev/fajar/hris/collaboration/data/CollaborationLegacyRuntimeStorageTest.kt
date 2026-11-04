package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeCompletionsRecord
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationLegacyRuntimeStorageTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun exactLeaseGuardSerializesPublicationObservationWithFinallyCleanup() {
        with(Fixture()) {
            migrate()
            success(legacy { repository.ensure(key, now()) })
            val lease =
                requireNotNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            val entered = CountDownLatch(1)
            val leave = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val observation =
                    pool.submit<CollaborationLegacyRuntimeLease> {
                        requireNotNull(
                            success(
                                root {
                                    repository.find(key, true).flatMap {
                                        repository.lease(key, lease.slot, true).map { value ->
                                            entered.countDown()
                                            check(leave.await(5, TimeUnit.SECONDS))
                                            value
                                        }
                                    }
                                }
                            )
                        )
                    }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertInstanceOf(
                    Result.Failed::class.java,
                    run {
                        runtime.queryForList(
                            "select * from collaboration_legacy_runtime_leases where token=? for update nowait",
                            lease.token,
                        )
                    },
                )
                val cleanup = pool.submit<Boolean> { success(root { repository.release(lease) }) }
                assertFalse(cleanup.isDone)
                leave.countDown()
                assertEquals(lease, observation.get(10, TimeUnit.SECONDS))
                assertTrue(cleanup.get(10, TimeUnit.SECONDS))
                assertNotNull(
                    success(root { repository.lease(key, lease.slot, false) })?.releasedAt
                )
            } finally {
                leave.countDown()
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun typedOwnershipAndEveryReleaseCoordinateFenceAReusedSlotAcrossFreeze() {
        with(Fixture()) {
            migrate()
            assertNull(success(root { repository.find(key, false) }))
            val initial = success(legacy { repository.ensure(key, now()) })
            assertEquals(initial, success(legacy { repository.ensure(key, now()) }))
            val lease =
                requireNotNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            val wrong =
                listOf(
                    lease.copy(owner = key.copy(companyId = UUID.randomUUID())),
                    lease.copy(owner = key.copy(deviceId = UUID.randomUUID())),
                    lease.copy(owner = key.copy(accountId = UUID.randomUUID())),
                    lease.copy(owner = key.copy(nativeSessionId = UUID.randomUUID())),
                    lease.copy(slot = 1),
                    lease.copy(token = UUID.randomUUID()),
                    lease.copy(startedAt = lease.startedAt.plusNanos(1000)),
                    lease.copy(expiresAt = lease.expiresAt.minusNanos(1000)),
                )
            for (stale in wrong) assertFalse(success(root { repository.release(stale) }))
            assertEquals(listOf(lease), success(root { repository.slots(key) }))
            assertTrue(success(root { repository.release(lease) }))
            val replacement =
                requireNotNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            assertEquals(lease.slot, replacement.slot)
            assertNotEquals(lease.token, replacement.token)
            assertFalse(success(root { repository.release(lease) }))
            val target = target()
            assertFalse(
                success(
                    root {
                        repository.freeze(
                            key.copy(nativeSessionId = UUID.randomUUID()),
                            target,
                            0,
                            now(),
                        )
                    }
                )
            )
            assertTrue(success(root { repository.freeze(key, target, 0, now()) }))
            val frozen = requireNotNull(success(root { repository.find(key, true) }))
            assertEquals(CollaborationLegacyRuntimePhase.DRAINING, frozen.phase)
            assertEquals(target, frozen.target)
            assertEquals(frozen, success(legacy { repository.ensure(key, now()) }))
            assertNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            assertFalse(success(root { repository.freeze(key, target, 0, now()) }))
            assertTrue(success(root { repository.release(replacement) }))
            assertFalse(success(root { repository.release(replacement) }))
            assertNull(
                success(
                    transactions.run(actor.copy(accountId = peer)) { repository.find(key, false) }
                )
            )
            assertTrue(
                success(transactions.run(actor.copy(accountId = peer)) { repository.slots(key) })
                    .isEmpty()
            )
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(actor.copy(companyId = employer)) {
                    repository.claim(key, now().plusSeconds(30))
                },
            )
        }
    }

    @Test
    fun callerRollbackCancellationAndInterruptionPreserveTheHeadAndLiveLease() {
        with(Fixture()) {
            migrate()
            val initial = success(legacy { repository.ensure(key, now()) })
            val failed = Result.Failed(Failure(FailureKind.CONFLICT, "fixture_failure"))
            assertEquals(
                failed,
                legacy { repository.claim(key, now().plusSeconds(30)).flatMap { failed } },
            )
            assertTrue(success(root { repository.slots(key) }).isEmpty())
            for (failure in
                listOf(
                    CancellationException("owned fixture"),
                    InterruptedException("owned fixture"),
                )) {
                assertThrows(failure.javaClass) {
                    legacy<Unit> {
                        repository.claim(key, now().plusSeconds(30)).flatMap { throw failure }
                    }
                }
                assertTrue(success(root { repository.slots(key) }).isEmpty())
            }
            val lease =
                requireNotNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            assertEquals(failed, root { repository.release(lease).flatMap { failed } })
            assertEquals(listOf(lease), success(root { repository.slots(key) }))
            val target = target()
            assertEquals(
                failed,
                root { repository.freeze(key, target, 0, now()).flatMap { failed } },
            )
            assertEquals(initial, success(root { repository.find(key, false) }))
            assertTrue(success(root { repository.release(lease) }))
        }
    }

    @Test
    fun ownedMetadataAndExactCleanupRemainAvailableAfterNativeParentPurge() {
        with(Fixture()) {
            migrate()
            success(legacy { repository.ensure(key, now()) })
            val lease =
                requireNotNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            val target = target()
            success(root { repository.freeze(key, target, 0, now()) })
            val retained = success(root { repository.find(key, false) })
            sql.update(
                "update company_memberships set active=false,version=version+1 where account_id=?",
                account,
            )
            sql.update("delete from native_sessions where id=?", parent)
            assertEquals(retained, success(root { repository.find(key, false) }))
            assertEquals(listOf(lease), success(root { repository.slots(key) }))
            assertTrue(success(root { repository.release(lease) }))
            assertFalse(success(root { repository.release(lease) }))
            assertNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            assertInstanceOf(Result.Failed::class.java, legacy { repository.ensure(key, now()) })
            assertEquals(retained, success(root { repository.find(key, false) }))
            val released = success(root { repository.slots(key) }).single()
            assertEquals(lease, released.copy(releasedAt = null))
            assertNotNull(released.releasedAt)
        }
    }

    @Test
    fun completionPortRetainsExactSourceAndTargetGenerationsWithoutGatewayMaterial() {
        with(Fixture(completion = true)) {
            migrate()
            prepareCompletion(withGateway = false)
            val request = completionRequest()
            assertNull(success(root { completions.find(key) }))
            val completed =
                success(
                    root {
                        publishCompletion()
                        completions.complete(request)
                    }
                )
            assertEquals(key, completed.owner)
            assertEquals(request.target, completed.target)
            assertEquals(2, completed.sourceDeviceVersion)
            assertEquals(1, completed.sourceProviderGeneration)
            assertEquals(1, completed.targetDeviceVersion)
            assertEquals(0, completed.targetProviderGeneration)
            assertNull(completed.sourceGateway)
            assertEquals(completed, success(root { completions.find(key) }))
            for (wrong in
                listOf(
                    key.copy(companyId = UUID.randomUUID()),
                    key.copy(deviceId = UUID.randomUUID()),
                    key.copy(accountId = peer),
                    key.copy(nativeSessionId = UUID.randomUUID()),
                )) assertNull(success(root { completions.find(wrong) }))
            assertEquals(
                "REVOKED",
                sql.queryForObject(
                    "select state from collaboration_devices where id=?",
                    String::class.java,
                    legacyDevice,
                ),
            )
            assertNull(
                sql.queryForObject(
                    "select token_encrypted from collaboration_provider_sessions where device_id=?",
                    String::class.java,
                    legacyDevice,
                )
            )
            assertEquals(
                CollaborationLegacyRuntimePhase.DRAINING,
                success(root { repository.find(key, false) })?.phase,
            )
        }
    }

    @Test
    fun completionPortReadbackFailuresRollBackActualRetirementAndAllowTheSameRetry() {
        with(Fixture(completion = true)) {
            migrate()
            prepareCompletion()
            val request = completionRequest()
            val original = completionSnapshot()
            val cancel = CancellationException("Owned completion readback cancellation")
            val interrupt = InterruptedException("Owned completion readback interruption")
            for (mode in listOf("missing", "foreign", "timeout", "cancel", "interrupt", "flag")) {
                var reads = 0
                var writes = 0
                val injected =
                    object : CollaborationLegacyRuntimeCompletionDataSource by completionSource {
                        override fun complete(
                            request: CompleteCollaborationLegacyRuntimeData
                        ): Boolean {
                            val result = completionSource.complete(request)
                            writes++
                            if (mode == "flag") Thread.currentThread().interrupt()
                            return result
                        }

                        override fun find(
                            owner: CollaborationLegacyRuntimeCoordinatesData
                        ): CollaborationLegacyRuntimeCompletionsRecord? {
                            reads++
                            val row = completionSource.find(owner)
                            return when (mode) {
                                "missing" -> null
                                "foreign" -> requireNotNull(row).apply { accountId = peer }
                                "timeout" ->
                                    throw org.springframework.dao.QueryTimeoutException(
                                        "Owned readback timeout"
                                    )
                                "cancel" -> throw cancel
                                "interrupt" -> throw interrupt
                                else ->
                                    error(
                                        "An interrupted completion must not start a readback query"
                                    )
                            }
                        }
                    }
                val repository = StoredCollaborationLegacyRuntimeCompletionRepository(injected)
                fun run() = root {
                    publishCompletion()
                    repository.complete(request)
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
                assertEquals(1, writes)
                assertEquals(if (mode == "flag") 0 else 1, reads)
                assertEquals(original, completionSnapshot())
            }
            val completed =
                success(
                    root {
                        publishCompletion()
                        completions.complete(request)
                    }
                )
            assertEquals(
                CollaborationLegacyGatewayRetirement(requireNotNull(gatewayId), 1),
                completed.sourceGateway,
            )
        }
    }

    @Test
    fun completionPortRejectsWrongOriginalOwnerAndStaleGenerationsWithoutPartialPublication() {
        with(Fixture(completion = true)) {
            migrate()
            prepareCompletion()
            val request = completionRequest()
            val original = completionSnapshot()
            for (wrong in
                listOf(
                    request.copy(owner = key.copy(companyId = UUID.randomUUID())),
                    request.copy(owner = key.copy(deviceId = UUID.randomUUID())),
                    request.copy(owner = key.copy(accountId = peer)),
                    request.copy(owner = key.copy(nativeSessionId = UUID.randomUUID())),
                    request.copy(target = request.target.copy(deviceId = UUID.randomUUID())),
                    request.copy(nativeSessionVersion = 1),
                    request.copy(legacyDeviceVersion = 2),
                    request.copy(legacyProviderGeneration = 1),
                    request.copy(legacyGatewayVersion = null),
                    request.copy(targetProviderGeneration = 1),
                    request.copy(fingerprint = "b".repeat(64)),
                )) {
                assertInstanceOf(
                    Result.Failed::class.java,
                    root {
                        publishCompletion()
                        completions.complete(wrong)
                    },
                )
                assertEquals(original, completionSnapshot())
            }
            success(
                root {
                    publishCompletion()
                    completions.complete(request)
                }
            )
        }
    }

    @Test
    fun completionPortMapsActualApiCapabilityDenialWithoutCreatingMetadata() {
        with(Fixture(completion = true, workerRole = false)) {
            migrate()
            val request =
                CompleteCollaborationLegacyRuntimeRequest(
                    key,
                    CollaborationLegacyRuntimeTarget(space, UUID.randomUUID()),
                    0,
                    1,
                    0,
                    0,
                    1,
                    0,
                    null,
                    fingerprint,
                    now(),
                )
            val denied = root { completions.complete(request) }
            assertEquals("access_denied", (denied as Result.Failed).failure.code)
            assertNull(success(root { completions.find(key) }))
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

    private inner class Fixture(
        val completion: Boolean = false,
        val workerRole: Boolean = completion,
    ) {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val sql = JdbcTemplate(source)
        val runtimeSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "legacy_runtime_fixture",
                "isolated-fixture-only",
            )
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val repository =
            StoredCollaborationLegacyRuntimeRepository(
                PostgresCollaborationLegacyRuntimeDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(runtimeSource), SQLDialect.POSTGRES)
                )
            )
        val completionSource =
            PostgresCollaborationLegacyRuntimeCompletionDataSource(
                DSL.using(TransactionAwareDataSourceProxy(runtimeSource), SQLDialect.POSTGRES)
            )
        val completions = StoredCollaborationLegacyRuntimeCompletionRepository(completionSource)
        var gatewayId: UUID? = null
        var targetId: UUID? = null
        var claimed: Map<String, Any?>? = null
        var keysObservedAt: Instant? = null
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
            Flyway.configure()
                .dataSource(source)
                .target(if (completion) "124" else "123")
                .load()
                .migrate()
            sql.execute("create role legacy_runtime_fixture login password 'isolated-fixture-only'")
            sql.execute("grant usage on schema public to legacy_runtime_fixture")
            if (workerRole) sql.execute("grant hris_worker_capability to legacy_runtime_fixture")
            sql.execute(
                "grant select,insert,update,delete on all tables in schema public to legacy_runtime_fixture"
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

        fun prepareCompletion(withGateway: Boolean = true) {
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
            val target = target().deviceId
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
            success(legacy { repository.ensure(key, now()) })
            assertTrue(
                success(
                    root {
                        repository.freeze(
                            key,
                            CollaborationLegacyRuntimeTarget(space, target),
                            0,
                            now(),
                        )
                    }
                )
            )
            claimed =
                success(
                    run {
                        runtime
                            .queryForList(
                                "select * from claim_collaboration_space_provisioning(?,1,45)",
                                UUID.randomUUID(),
                            )
                            .single()
                    }
                )
            keysObservedAt = now()
        }

        fun publishCompletion() {
            val target = requireNotNull(targetId)
            val work = requireNotNull(claimed)
            val current = now().toString()
            runtime.update(
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
                runtime.update(
                    "update collaboration_space_devices set state='ACTIVE',version=1,updated_at=?::timestamptz where space_id=? and id=? and version=0",
                    current,
                    space,
                    target,
                ),
            )
            assertEquals(
                1,
                runtime.update(
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

        fun completionRequest() =
            CompleteCollaborationLegacyRuntimeRequest(
                key,
                CollaborationLegacyRuntimeTarget(space, requireNotNull(targetId)),
                0,
                1,
                0,
                0,
                1,
                0,
                if (gatewayId == null) null else 0L,
                fingerprint,
                requireNotNull(keysObservedAt),
            )

        fun completionSnapshot(): Map<String, List<Map<String, Any?>>> =
            listOf(
                    "collaboration_devices",
                    "collaboration_provider_sessions",
                    "collaboration_gateway_sessions",
                    "collaboration_space_devices",
                    "collaboration_space_provider_sessions",
                    "collaboration_space_provisioning",
                    "collaboration_legacy_runtime",
                    "collaboration_legacy_runtime_completions",
                )
                .associateWith { sql.queryForList("select * from $it") }

        val key
            get() = CollaborationLegacyRuntimeOwner(company, legacyDevice, account, parent)

        fun <T> legacy(body: () -> Result<T>): Result<T> =
            transactions.run(actor.copy(companyId = company), body)

        fun <T> root(body: () -> Result<T>): Result<T> = transactions.run(actor, body)

        fun target(): CollaborationLegacyRuntimeTarget =
            UUID.randomUUID().let { id ->
                success(run { insert(id) })
                success(run { bind(id) })
                CollaborationLegacyRuntimeTarget(space, id)
            }
    }

    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
