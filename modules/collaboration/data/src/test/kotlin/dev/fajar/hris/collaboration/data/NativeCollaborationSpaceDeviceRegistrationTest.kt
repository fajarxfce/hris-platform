package dev.fajar.hris.collaboration.data

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.*
import dev.fajar.hris.core.database.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.parseIdentityKeyring
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper

/** Real RLS, guards, sessions, receipts and journal; hooks inject failures after actual writes. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NativeCollaborationSpaceDeviceRegistrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    private lateinit var owner: JdbcTemplate
    private lateinit var runtime: DriverManagerDataSource
    private lateinit var space: UUID
    private lateinit var now: Instant
    private val server = "matrix.example.test"
    private val client = ClientVersion(ClientPlatform.ANDROID, 28)

    @BeforeAll
    fun database() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).load().migrate()
        owner = JdbcTemplate(source)
        owner.execute(
            "create role space_device_command_fixture login password 'isolated-fixture-only'"
        )
        owner.execute("grant usage on schema public to space_device_command_fixture")
        owner.execute("grant select on all tables in schema public to space_device_command_fixture")
        owner.execute(
            "grant update on accounts,native_sessions,collaboration_space_devices to space_device_command_fixture"
        )
        owner.execute(
            "grant insert on collaboration_space_provisioning,collaboration_space_identities,collaboration_space_devices,collaboration_space_device_sessions,operation_receipts,audit_entries,platform_audit_metadata,outbox_events to space_device_command_fixture"
        )
        owner.execute(
            "grant insert,update on collaboration_legacy_runtime,collaboration_legacy_runtime_leases to space_device_command_fixture"
        )
        runtime =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "space_device_command_fixture",
                "isolated-fixture-only",
            )
        now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        space =
            requireNotNull(
                owner.queryForObject("select id from collaboration_spaces", UUID::class.java)
            )
    }

    @BeforeEach
    fun clockForTest() {
        now = Instant.now().truncatedTo(ChronoUnit.MICROS)
    }

    @Test
    fun explicitSelectionPrecedesRegistrationAndLostResponsesKeepTheSameDevice() {
        val f = Fixture()
        val operation = UUID.randomUUID()
        failure(f.register(operation), "collaboration_identity_selection_required")
        f.counts(0, 0, 0)
        f.select()
        val receipt = success(f.register(operation))
        assertEquals(0, receipt.version)
        val device = f.device()
        val work = f.work(device.id)
        assertEquals(device.id, work.request.deviceId)
        assertEquals(f.session, work.request.nativeSessionId)
        assertEquals(f.actor.authenticatedAt, work.request.authenticatedAt)
        assertEquals(f.actor.mfaVerifiedAt, work.request.mfaVerifiedAt)
        assertEquals(f.actor.correlationId, work.request.correlationId)
        assertEquals(client, work.request.client)
        assertEquals(0, work.request.nativeSessionVersion)
        assertEquals(0, work.request.credentialVersion)
        assertEquals(0, work.request.revision)
        assertEquals(now, work.request.requestedAt)
        assertEquals(now.plusSeconds(60), work.request.expiresAt)
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, work.state)
        assertEquals(0, work.attempts)
        assertNull(work.lease)
        assertNull(work.lastOutcome)
        assertEquals(receipt.id, device.id)
        assertEquals("HRISG_${compact(device.id)}", device.matrixDeviceId)
        assertEquals(f.session, device.originNativeSessionId)
        assertEquals(receipt.copy(replayed = true), success(f.register(operation)))
        assertEquals(receipt, success(f.register(UUID.randomUUID())))
        f.counts(1, 2, 1)
        assertEquals(work, f.work(device.id))
        owner.update(
            "update collaboration_space_devices set state='ACTIVE',version=1 where id=?",
            device.id,
        )
        assertEquals(receipt.copy(replayed = true), success(f.register(operation)))
        failure(
            f.register(operation, request = f.actor.copy(companyId = f.company)),
            "platform_scope_required",
        )
        failure(f.register(operation, build = client.copy(build = 27)), "client_update_required")
        failure(f.register(operation, version = 1), "native_session_invalid")
        failure(
            f.register(operation, requestedServer = "changed.example.test"),
            "operation_payload_mismatch",
        )
        failure(
            f.register(UUID.randomUUID(), requestedServer = "changed.example.test"),
            "collaboration_identity_migration_required",
        )
        owner.update(
            "update native_sessions set revoked_at=?::timestamptz,version=version+1 where id=?",
            now.toString(),
            f.session,
        )
        failure(f.register(operation), "native_session_invalid")
        f.counts(1, 2, 1)
    }

    @Test
    fun refreshedNativeReplayPreservesTheOriginalWorkAndParentDeadlineBoundsNewWork() {
        val f = Fixture()
        f.select()
        owner.update(
            "update native_sessions set access_expires_at=?::timestamptz where id=?",
            now.plusSeconds(20).toString(),
            f.session,
        )
        val operation = UUID.randomUUID()
        val receipt = success(f.register(operation))
        val original = f.work(receipt.id)
        assertEquals(now.plusSeconds(20), original.request.expiresAt)
        owner.update(
            "update native_sessions set version=version+1,rotated_at=?::timestamptz,access_expires_at=?::timestamptz where id=?",
            now.toString(),
            now.plusSeconds(900).toString(),
            f.session,
        )
        failure(f.register(operation), "native_session_invalid")
        assertEquals(receipt.copy(replayed = true), success(f.register(operation, version = 1)))
        assertEquals(receipt, success(f.register(UUID.randomUUID(), version = 1)))
        assertEquals(original, f.work(receipt.id))
        f.counts(1, 2, 1)
    }

    @Test
    fun retainedIntentNeedsTheExactSourceWhileNewDevicesKeepDistinctIdsUnderTheSameUser() {
        val f = Fixture()
        val legacy = f.activeLegacy()
        f.select()
        val original =
            owner.queryForList(
                "select * from collaboration_provider_sessions where account_id=?",
                f.account,
            )
        failure(
            f.register(UUID.randomUUID(), legacy = legacy, fingerprint = "b".repeat(64)),
            "collaboration_device_migration_required",
        )
        failure(
            f.register(UUID.randomUUID(), legacy = legacy, session = f.parent()),
            "collaboration_device_migration_required",
        )
        val operation = UUID.randomUUID()
        val receipt = success(f.register(operation, legacy = legacy))
        val retained = f.device()
        assertEquals(CollaborationSpaceDeviceOrigin.LEGACY, retained.origin)
        assertEquals("HRIS_${compact(legacy.deviceId)}", retained.matrixDeviceId)
        assertEquals("a".repeat(64), retained.legacyKeyFingerprint)
        assertEquals(receipt.copy(replayed = true), success(f.register(operation, legacy = legacy)))
        failure(f.register(UUID.randomUUID()), "collaboration_device_changed")
        val newParent = f.parent()
        success(f.register(UUID.randomUUID(), session = newParent))
        val fresh = f.device(newParent)
        assertEquals(retained.matrixUserId, fresh.matrixUserId)
        assertEquals(CollaborationSpaceDeviceOrigin.SPACE, fresh.origin)
        assertNotEquals(retained.matrixDeviceId, fresh.matrixDeviceId)
        assertNull(fresh.legacyKeyFingerprint)
        assertEquals(
            original,
            owner.queryForList(
                "select * from collaboration_provider_sessions where account_id=?",
                f.account,
            ),
        )
        f.counts(2, 2, 2)
    }

    @Test
    fun concurrentFifthAndSixthRegistrationsSerializeOnTheRealAccountGuard() {
        val f = Fixture()
        f.select()
        repeat(4) { success(f.register(UUID.randomUUID(), session = f.parent())) }
        val parents = listOf(f.parent(), f.parent())
        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results =
                parents
                    .map { parent ->
                        pool.submit<Result<MutationReceipt>> {
                            barrier.await(5, TimeUnit.SECONDS)
                            f.register(UUID.randomUUID(), session = parent)
                        }
                    }
                    .map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is Result.Success })
            assertEquals(1, results.count { it is Result.Failed })
            failure(results.first { it is Result.Failed }, "collaboration_device_limit")
            f.counts(5, 5, 5)
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun failuresAndCancellationAfterEachWriteRollBackRegistrationAndAllowAnIdenticalRetry() {
        for (stage in listOf("device", "queue", "receipt", "journal")) for (mode in
            listOf("failure", "cancel", "interrupt")) {
            val f = Fixture()
            f.select()
            val operation = UUID.randomUUID()
            fun injected(): Result<Unit> {
                if (mode == "cancel") throw CancellationException("fixture cancellation")
                if (mode == "interrupt") throw InterruptedException("fixture interruption")
                return Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_write_failed"))
            }
            val registry =
                object : CollaborationSpaceDeviceRepository by f.registry {
                    override fun register(device: CollaborationSpaceDevice): Result<Unit> =
                        f.registry.register(device).flatMap {
                            if (stage == "device") injected() else Result.Success(Unit)
                        }
                }
            val provisioning =
                object : CollaborationSpaceProvisioningRepository by f.provisioning {
                    override fun enqueue(
                        request: CollaborationSpaceProvisioningRequest
                    ): Result<Unit> =
                        f.provisioning.enqueue(request).flatMap {
                            if (stage == "queue") injected() else Result.Success(Unit)
                        }
                }
            val operations =
                object : OperationRepository by f.operations {
                    override fun record(
                        actor: Actor,
                        key: OperationKey,
                        receipt: MutationReceipt,
                    ): Result<Unit> =
                        f.operations.record(actor, key, receipt).flatMap {
                            if (stage == "receipt") injected() else Result.Success(Unit)
                        }
                }
            val journal = hookJournal { actor, change ->
                f.journal.record(actor, change).flatMap {
                    if (stage == "journal") injected() else Result.Success(Unit)
                }
            }
            fun run() =
                f.register(
                    operation,
                    registry = registry,
                    provisioning = provisioning,
                    operations = operations,
                    journal = journal,
                )
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { run() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { run() }
                else -> failure(run(), "fixture_write_failed")
            }
            f.counts(0, 0, 0)
            assertFalse(success(f.register(operation)).replayed)
            f.counts(1, 1, 1)
        }
    }

    @Test
    fun completionRejectsLateGroupMfaLegacyClockExpiryAndInterruptedChangesWithoutPartialWrites() {
        for (change in
            listOf("offboard", "mfa", "legacy", "expiry", "backward", "budget", "interrupt")) {
            now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val f = Fixture()
            val legacy = if (change == "legacy") f.activeLegacy() else null
            f.select()
            if (change == "expiry")
                owner.update(
                    "update native_sessions set access_expires_at=?::timestamptz where id=?",
                    now.plusSeconds(1).toString(),
                    f.session,
                )
            val journal = hookJournal { actor, record ->
                f.journal.record(actor, record).map {
                    when (change) {
                        "offboard" ->
                            owner.update(
                                "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                                f.company,
                                f.account,
                            )
                        "mfa" ->
                            owner.update(
                                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'identity.manage')",
                                f.company,
                                f.account,
                            )
                        "legacy" ->
                            owner.update(
                                "update collaboration_devices set state='REVOKED',version=2,revoked_at=?::timestamptz,updated_at=?::timestamptz where company_id=? and id=?",
                                now.toString(),
                                now.toString(),
                                legacy!!.companyId,
                                legacy.deviceId,
                            )
                        "expiry" -> f.clock.at = now.plusSeconds(1)
                        "backward" -> f.clock.at = now.minusSeconds(1)
                        "budget" -> f.clock.at = now.plusSeconds(30)
                        "interrupt" -> Thread.currentThread().interrupt()
                    }
                    Unit
                }
            }
            if (change == "interrupt") {
                try {
                    assertThrows(InterruptedException::class.java) {
                        f.register(UUID.randomUUID(), legacy = legacy, journal = journal)
                    }
                    assertTrue(Thread.currentThread().isInterrupted)
                } finally {
                    Thread.interrupted()
                }
            } else {
                val code =
                    when (change) {
                        "offboard" -> "collaboration_access_denied"
                        "mfa" -> "mfa_setup_required"
                        "legacy" -> "collaboration_device_migration_required"
                        "expiry" -> "native_session_invalid"
                        else -> "collaboration_admission_expired"
                    }
                failure(f.register(UUID.randomUUID(), legacy = legacy, journal = journal), code)
            }
            f.counts(0, 0, 0)
        }
    }

    @Test
    fun groupStatusPreservesPendingWorkAcrossRefreshAndExpiryWithoutWriting() {
        val f = Fixture()
        assertNull(success(f.status()))
        f.select()
        val receipt = success(f.register(UUID.randomUUID()))
        val original = f.work(receipt.id)
        val first = requireNotNull(success(f.status()))
        assertEquals(receipt.id, first.id)
        assertEquals(0, first.version)
        assertEquals(CollaborationDeviceState.PROVISIONING, first.state)
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, first.provisioning.state)
        assertEquals(now.plusSeconds(60), first.provisioning.retryNotBefore)
        assertEquals(now.plusSeconds(60), first.provisioning.expiresAt)
        assertEquals(now, first.observedAt)
        owner.update(
            "update native_sessions set version=1,rotated_at=?::timestamptz where id=?",
            now.toString(),
            f.session,
        )
        failure(f.status(), "native_session_invalid")
        assertEquals(first, success(f.status(version = 1)))
        f.clock.at = now.plusSeconds(60)
        val expired = requireNotNull(success(f.status(version = 1)))
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, expired.provisioning.state)
        assertEquals(expired.observedAt, expired.provisioning.retryNotBefore)
        assertEquals(original, f.work(receipt.id))
        assertEquals(0, original.request.nativeSessionVersion)
        f.counts(1, 1, 1)
        owner.update(
            "update collaboration_space_devices set state='REVOKED',version=1,updated_at=?::timestamptz,revoked_at=?::timestamptz where id=?",
            now.toString(),
            now.toString(),
            receipt.id,
        )
        val revoked = requireNotNull(success(f.status(version = 1)))
        assertEquals(CollaborationDeviceState.REVOKED, revoked.state)
        assertNull(revoked.provisioning.retryNotBefore)
        assertEquals(original, f.work(receipt.id))
        f.counts(1, 1, 1)
    }

    @Test
    fun groupStatusRechecksAuthorityAfterActualReadsAndRejectsMismatchedMetadata() {
        val f = Fixture()
        f.select()
        success(f.register(UUID.randomUUID()))
        failure(f.status(request = f.actor.copy(companyId = f.company)), "platform_scope_required")
        failure(f.status(build = client.copy(build = 27)), "client_update_required")
        failure(f.status(requestedSpace = UUID.randomUUID()), "collaboration_access_denied")
        failure(f.status(parent = UUID.randomUUID()), "native_session_invalid")
        val disabled =
            object : CollaborationSpaceDeviceRepository by f.registry {
                override fun findBound(
                    spaceId: UUID,
                    accountId: UUID,
                    nativeSessionId: UUID,
                ): Result<CollaborationSpaceDevice?> =
                    error("Disabled status must not read metadata")
            }
        failure(
            f.status(registry = disabled, policy = CollaborationSpacePolicy()),
            "collaboration_unavailable",
        )
        for (variant in 0..3) {
            val changed =
                object : CollaborationSpaceProvisioningRepository by f.provisioning {
                    override fun metadata(
                        spaceId: UUID,
                        accountId: UUID,
                        deviceId: UUID,
                        lock: Boolean,
                    ): Result<CollaborationSpaceProvisioning?> =
                        f.provisioning.metadata(spaceId, accountId, deviceId, lock).map { head ->
                            when (variant) {
                                0 -> null
                                1 ->
                                    requireNotNull(head)
                                        .copy(
                                            request =
                                                head.request.copy(
                                                    nativeSessionId = UUID.randomUUID()
                                                )
                                        )
                                2 -> {
                                    owner.update(
                                        "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                                        f.company,
                                        f.account,
                                    )
                                    head
                                }
                                else -> {
                                    f.clock.at = now.plusSeconds(30)
                                    head
                                }
                            }
                        }
                }
            failure(
                f.status(provisioning = changed),
                when (variant) {
                    2 -> "collaboration_access_denied"
                    3 -> "collaboration_admission_changed"
                    else -> "invalid_collaboration_provisioning"
                },
            )
            if (variant == 2)
                owner.update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                    f.company,
                    f.account,
                )
            f.clock.at = now
        }
        f.counts(1, 1, 1)
    }

    @Test
    fun groupStatusPreservesFailuresCancellationAndInterruptionWithoutMutatingWork() {
        val f = Fixture()
        f.select()
        val receipt = success(f.register(UUID.randomUUID()))
        val original = f.work(receipt.id)
        val failed = Result.Failed(Failure(FailureKind.UNAVAILABLE, "database_busy"))
        for (variant in 0..2) {
            val interrupted =
                object : CollaborationSpaceProvisioningRepository by f.provisioning {
                    override fun metadata(
                        spaceId: UUID,
                        accountId: UUID,
                        deviceId: UUID,
                        lock: Boolean,
                    ): Result<CollaborationSpaceProvisioning?> =
                        f.provisioning.metadata(spaceId, accountId, deviceId, lock).flatMap {
                            when (variant) {
                                0 -> failed
                                1 -> throw CancellationException("Owned status cancellation")
                                else -> throw InterruptedException("Owned status interruption")
                            }
                        }
                }
            when (variant) {
                0 -> assertEquals(failed, f.status(provisioning = interrupted))
                1 ->
                    assertThrows(CancellationException::class.java) {
                        f.status(provisioning = interrupted)
                    }
                else ->
                    assertThrows(InterruptedException::class.java) {
                        f.status(provisioning = interrupted)
                    }
            }
            assertEquals(original, f.work(receipt.id))
        }
        f.counts(1, 1, 1)
    }

    @Test
    fun freezeInitializesRootOwnedMetadataAndLostResponsesRetainOneTargetAcrossNativeRefresh() {
        val f = Fixture()
        val legacy = f.activeLegacy()
        f.select()
        val target = success(f.register(UUID.randomUUID(), legacy = legacy))
        val device = f.device()
        val work = f.work(target.id)
        val operation = UUID.randomUUID()
        assertNull(f.frozen(legacy))
        val receipt = success(f.freeze(operation, target.id))
        assertEquals(MutationReceipt(legacy.deviceId, 1), receipt)
        val frozen = requireNotNull(f.frozen(legacy))
        assertEquals(CollaborationLegacyRuntimePhase.DRAINING, frozen.phase)
        assertEquals(CollaborationLegacyRuntimeTarget(space, target.id), frozen.target)
        assertEquals(f.session, frozen.owner.nativeSessionId)
        f.freezeCounts(1, 1, 1)
        assertEquals(receipt.copy(replayed = true), success(f.freeze(operation, target.id)))
        assertEquals(receipt, success(f.freeze(UUID.randomUUID(), target.id)))
        owner.update(
            "update native_sessions set version=1,rotated_at=?::timestamptz where id=?",
            now.toString(),
            f.session,
        )
        failure(f.freeze(operation, target.id), "native_session_invalid")
        assertEquals(
            receipt.copy(replayed = true),
            success(f.freeze(operation, target.id, nativeVersion = 1)),
        )
        assertEquals(frozen, f.frozen(legacy))
        assertEquals(device, f.device())
        assertEquals(work, f.work(target.id))
        assertEquals(
            "ACTIVE",
            owner.queryForObject(
                "select state from collaboration_devices where company_id=? and id=?",
                String::class.java,
                legacy.companyId,
                legacy.deviceId,
            ),
        )
        assertEquals(
            0,
            owner.queryForObject(
                "select count(*) from collaboration_space_provider_sessions where account_id=?",
                Int::class.java,
                f.account,
            ),
        )
        f.freezeCounts(1, 2, 1)
        f.counts(1, 1, 1)
    }

    @Test
    fun freezeRejectsSubstitutedScopeTargetVersionFingerprintAndRevokedReplay() {
        val f = Fixture()
        val legacy = f.activeLegacy()
        f.select()
        val target = success(f.register(UUID.randomUUID(), legacy = legacy))
        val operation = UUID.randomUUID()
        val foreign = Fixture()
        for (result in
            listOf(
                f.freeze(operation, UUID.randomUUID()),
                f.freeze(operation, target.id, request = f.actor.copy(companyId = f.company)),
                f.freeze(operation, target.id, request = foreign.actor, parent = foreign.session),
                f.freeze(operation, target.id, parent = f.parent()),
                f.freeze(operation, target.id, nativeVersion = 1),
                f.freeze(operation, target.id, deviceVersion = 1),
                f.freeze(operation, target.id, requestedSpace = UUID.randomUUID()),
                f.freeze(operation, target.id, build = client.copy(build = 27)),
                f.freeze(operation, target.id, fingerprint = "b".repeat(64)),
                f.freeze(operation, target.id, fingerprint = "a".repeat(63)),
            )) assertInstanceOf(Result.Failed::class.java, result)
        f.freezeCounts(0, 0, 0)
        foreign.freezeCounts(0, 0, 0)
        success(f.freeze(operation, target.id))
        val frozen = f.frozen(legacy)
        failure(
            f.freeze(operation, target.id, fingerprint = "b".repeat(64)),
            "operation_payload_mismatch",
        )
        owner.update(
            "update native_sessions set revoked_at=?::timestamptz,version=1 where id=?",
            now.toString(),
            f.session,
        )
        failure(f.freeze(operation, target.id), "native_session_invalid")
        failure(f.freeze(UUID.randomUUID(), target.id), "native_session_invalid")
        assertEquals(frozen, f.frozen(legacy))
        f.freezeCounts(1, 1, 1)
        val fresh = Fixture()
        fresh.select()
        val newDevice = success(fresh.register(UUID.randomUUID()))
        failure(fresh.freeze(UUID.randomUUID(), newDevice.id), "collaboration_runtime_changed")
        fresh.freezeCounts(0, 0, 0)
    }

    @Test
    fun freezeFailuresAndCancellationAfterEveryWriteRollBackRuntimeReceiptAndJournal() {
        for (stage in listOf("ensure", "freeze", "receipt", "journal")) for (mode in
            listOf("failure", "cancel", "interrupt")) {
            now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val f = Fixture()
            val legacy = f.activeLegacy()
            f.select()
            val target = success(f.register(UUID.randomUUID(), legacy = legacy))
            val operation = UUID.randomUUID()
            val cancel = CancellationException("Owned freeze cancellation")
            val interrupt = InterruptedException("Owned freeze interruption")
            fun injected(): Result<Unit> =
                when (mode) {
                    "cancel" -> throw cancel
                    "interrupt" -> throw interrupt
                    else -> Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_write_failed"))
                }
            val runtimes =
                object : CollaborationLegacyRuntimeRepository by f.runtimes {
                    override fun ensure(
                        owner: CollaborationLegacyRuntimeOwner,
                        at: Instant,
                    ): Result<CollaborationLegacyRuntime> =
                        f.runtimes.ensure(owner, at).flatMap { row ->
                            if (stage == "ensure") injected().map { row } else Result.Success(row)
                        }

                    override fun freeze(
                        owner: CollaborationLegacyRuntimeOwner,
                        target: CollaborationLegacyRuntimeTarget,
                        expectedVersion: Long,
                        at: Instant,
                    ): Result<Boolean> =
                        f.runtimes.freeze(owner, target, expectedVersion, at).flatMap { changed ->
                            if (stage == "freeze") injected().map { changed }
                            else Result.Success(changed)
                        }
                }
            val operations =
                object : OperationRepository by f.operations {
                    override fun record(
                        actor: Actor,
                        key: OperationKey,
                        receipt: MutationReceipt,
                    ): Result<Unit> =
                        f.operations.record(actor, key, receipt).flatMap {
                            if (stage == "receipt") injected() else Result.Success(Unit)
                        }
                }
            val journal = hookJournal { actor, change ->
                f.journal.record(actor, change).flatMap {
                    if (stage == "journal") injected() else Result.Success(Unit)
                }
            }
            fun run() =
                f.freeze(
                    operation,
                    target.id,
                    runtimes = runtimes,
                    operations = operations,
                    journal = journal,
                )
            when (mode) {
                "cancel" ->
                    assertSame(cancel, assertThrows(CancellationException::class.java) { run() })
                "interrupt" ->
                    assertSame(interrupt, assertThrows(InterruptedException::class.java) { run() })
                else -> failure(run(), "fixture_write_failed")
            }
            f.freezeCounts(0, 0, 0)
            assertNull(f.frozen(legacy))
            assertFalse(success(f.freeze(operation, target.id)).replayed)
            f.freezeCounts(1, 1, 1)
            f.counts(1, 1, 1)
        }
    }

    @Test
    fun freezeCompletionRechecksCurrentGroupMfaSourceTimeAndInterruptionAfterWrites() {
        for (change in
            listOf("offboard", "mfa", "legacy", "expiry", "backward", "budget", "interrupt")) {
            now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val f = Fixture()
            val legacy = f.activeLegacy()
            f.select()
            val target = success(f.register(UUID.randomUUID(), legacy = legacy))
            if (change == "expiry")
                owner.update(
                    "update native_sessions set access_expires_at=?::timestamptz where id=?",
                    now.plusSeconds(1).toString(),
                    f.session,
                )
            val journal = hookJournal { actor, record ->
                f.journal.record(actor, record).map {
                    when (change) {
                        "offboard" ->
                            owner.update(
                                "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                                f.company,
                                f.account,
                            )
                        "mfa" ->
                            owner.update(
                                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'identity.manage')",
                                f.company,
                                f.account,
                            )
                        "legacy" ->
                            owner.update(
                                "update collaboration_devices set state='REVOKED',version=2,revoked_at=?::timestamptz,updated_at=?::timestamptz where company_id=? and id=?",
                                now.toString(),
                                now.toString(),
                                legacy.companyId,
                                legacy.deviceId,
                            )
                        "expiry" -> f.clock.at = now.plusSeconds(1)
                        "backward" -> f.clock.at = now.minusSeconds(1)
                        "budget" -> f.clock.at = now.plusSeconds(30)
                        "interrupt" -> Thread.currentThread().interrupt()
                    }
                    Unit
                }
            }
            if (change == "interrupt") {
                try {
                    assertThrows(InterruptedException::class.java) {
                        f.freeze(UUID.randomUUID(), target.id, journal = journal)
                    }
                    assertTrue(Thread.currentThread().isInterrupted)
                } finally {
                    Thread.interrupted()
                }
            } else {
                val code =
                    when (change) {
                        "offboard" -> "collaboration_access_denied"
                        "mfa" -> "mfa_setup_required"
                        "legacy" -> "collaboration_device_migration_required"
                        "expiry" -> "native_session_invalid"
                        else -> "collaboration_admission_expired"
                    }
                failure(f.freeze(UUID.randomUUID(), target.id, journal = journal), code)
            }
            f.freezeCounts(0, 0, 0)
            f.counts(1, 1, 1)
        }
    }

    @Test
    fun competingFreezeCommandsSerializeOneRuntimeTransitionAndRetainReplayReceipts() {
        for (sameOperation in listOf(true, false)) {
            val f = Fixture()
            val legacy = f.activeLegacy()
            f.select()
            val target = success(f.register(UUID.randomUUID(), legacy = legacy))
            val operation = UUID.randomUUID()
            val barrier = CyclicBarrier(2)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val results =
                    (0..1)
                        .map { index ->
                            pool.submit<MutationReceipt> {
                                barrier.await(5, TimeUnit.SECONDS)
                                success(
                                    f.freeze(
                                        if (index == 0 || sameOperation) operation
                                        else UUID.randomUUID(),
                                        target.id,
                                    )
                                )
                            }
                        }
                        .map { it.get(15, TimeUnit.SECONDS) }
                assertTrue(results.all { it.id == legacy.deviceId && it.version == 1L })
                assertEquals(if (sameOperation) 1 else 0, results.count { it.replayed })
                f.freezeCounts(1, if (sameOperation) 1 else 2, 1)
                assertEquals(
                    CollaborationLegacyRuntimeTarget(space, target.id),
                    f.frozen(legacy)?.target,
                )
            } finally {
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun freezeRetainsLiveProviderSlotsWithoutTreatingMetadataAsCompletedTransfer() {
        val f = Fixture()
        val legacy = f.activeLegacy()
        f.select()
        val target = success(f.register(UUID.randomUUID(), legacy = legacy))
        val runtimeOwner =
            CollaborationLegacyRuntimeOwner(legacy.companyId, legacy.deviceId, f.account, f.session)
        val companyActor = f.actor.copy(companyId = f.company)
        val lease =
            success(
                f.transactions.run(companyActor) {
                    f.runtimes.ensure(runtimeOwner, now).flatMap {
                        f.runtimes.claim(
                            runtimeOwner,
                            Instant.now().plusSeconds(45).truncatedTo(ChronoUnit.MICROS),
                        )
                    }
                }
            )
        assertNotNull(lease)
        f.clock.at = Instant.now().truncatedTo(ChronoUnit.MICROS)
        success(f.freeze(UUID.randomUUID(), target.id))
        assertEquals(
            listOf(lease),
            success(f.transactions.run(f.actor) { f.runtimes.slots(runtimeOwner) }),
        )
        assertNull(
            success(
                f.transactions.run(companyActor) {
                    f.runtimes.claim(
                        runtimeOwner,
                        Instant.now().plusSeconds(45).truncatedTo(ChronoUnit.MICROS),
                    )
                }
            )
        )
        assertTrue(
            success(f.transactions.run(companyActor) { f.runtimes.release(requireNotNull(lease)) })
        )
        assertEquals(CollaborationDeviceState.PROVISIONING, f.device().state)
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, f.work(target.id).state)
        f.freezeCounts(1, 1, 1)
    }

    @Test
    fun drainObservesActualExpiryAndCurrentNativeRefreshWithoutMutatingFrozenWork() {
        val f = Fixture()
        val legacy = f.activeLegacy()
        f.select()
        val target = success(f.register(UUID.randomUUID(), legacy = legacy))
        failure(f.drain(target.id), "collaboration_runtime_changed")
        f.freezeCounts(0, 0, 0)
        val lease = f.providerLease(legacy, seconds = 2)
        f.clock.at = Instant.now().truncatedTo(ChronoUnit.MICROS)
        success(f.freeze(UUID.randomUUID(), target.id))
        val frozen = f.frozen(legacy)
        val work = f.work(target.id)
        val busy = success(f.drain(target.id))
        assertEquals(1, busy.activeRequests)
        assertEquals(lease.expiresAt, busy.latestRequestExpiresAt)
        assertEquals(CollaborationLegacyRuntimeTarget(space, target.id), busy.target)
        owner.update(
            "update native_sessions set version=1,rotated_at=?::timestamptz where id=?",
            f.clock.at.toString(),
            f.session,
        )
        failure(f.drain(target.id), "native_session_invalid")
        assertEquals(busy, success(f.drain(target.id, nativeVersion = 1)))
        val remaining =
            Duration.between(Instant.now(), lease.expiresAt).toMillis().coerceAtLeast(0) + 10
        assertTrue(remaining <= 2010)
        Thread.sleep(remaining)
        f.clock.at = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val empty = success(f.drain(target.id, nativeVersion = 1))
        assertEquals(0, empty.activeRequests)
        assertNull(empty.latestRequestExpiresAt)
        assertEquals(1, empty.runtimeVersion)
        assertEquals(frozen, f.frozen(legacy))
        assertEquals(work, f.work(target.id))
        assertEquals(CollaborationDeviceState.PROVISIONING, f.device().state)
        assertEquals(
            listOf(lease),
            success(f.transactions.run(f.actor) { f.runtimes.slots(lease.owner) }),
        )
        f.freezeCounts(1, 1, 1)
        f.counts(1, 1, 1)
    }

    @Test
    fun drainPreservesReadFailuresAndRejectsLateAuthorityLossWithoutRetryOrWrites() {
        for (mode in listOf("failure", "cancel", "interrupt", "flag", "budget", "offboard")) {
            now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val f = Fixture()
            val legacy = f.activeLegacy()
            f.select()
            val target = success(f.register(UUID.randomUUID(), legacy = legacy))
            success(f.freeze(UUID.randomUUID(), target.id))
            val frozen = f.frozen(legacy)
            val cancel = CancellationException("Owned drain cancellation")
            val interrupt = InterruptedException("Owned drain interruption")
            var reads = 0
            val hooked =
                object : CollaborationLegacyRuntimeRepository by f.runtimes {
                    override fun slots(
                        owner: CollaborationLegacyRuntimeOwner
                    ): Result<List<CollaborationLegacyRuntimeLease>> =
                        f.runtimes.slots(owner).flatMap { slots ->
                            reads++
                            when (mode) {
                                "failure" ->
                                    Result.Failed(Failure(FailureKind.UNAVAILABLE, "database_busy"))
                                "cancel" -> throw cancel
                                "interrupt" -> throw interrupt
                                else -> {
                                    when (mode) {
                                        "flag" -> Thread.currentThread().interrupt()
                                        "budget" -> f.clock.at = now.plusSeconds(30)
                                        "offboard" ->
                                            this@NativeCollaborationSpaceDeviceRegistrationTest
                                                .owner
                                                .update(
                                                    "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                                                    f.company,
                                                    f.account,
                                                )
                                    }
                                    Result.Success(slots)
                                }
                            }
                        }
                }
            when (mode) {
                "cancel" ->
                    assertSame(
                        cancel,
                        assertThrows(CancellationException::class.java) {
                            f.drain(target.id, runtimes = hooked)
                        },
                    )
                "interrupt" ->
                    assertSame(
                        interrupt,
                        assertThrows(InterruptedException::class.java) {
                            f.drain(target.id, runtimes = hooked)
                        },
                    )
                "flag" ->
                    try {
                        assertThrows(InterruptedException::class.java) {
                            f.drain(target.id, runtimes = hooked)
                        }
                        assertTrue(Thread.currentThread().isInterrupted)
                    } finally {
                        Thread.interrupted()
                    }
                else ->
                    failure(
                        f.drain(target.id, runtimes = hooked),
                        when (mode) {
                            "failure" -> "database_busy"
                            "budget" -> "collaboration_admission_changed"
                            else -> "collaboration_access_denied"
                        },
                    )
            }
            assertEquals(1, reads)
            assertEquals(frozen, f.frozen(legacy))
            f.freezeCounts(1, 1, 1)
            f.counts(1, 1, 1)
        }
    }

    @Test
    fun drainRejectsSubstitutionAndConcurrentCleanupOnlyReducesTheNextObservation() {
        val f = Fixture()
        val legacy = f.activeLegacy()
        f.select()
        val target = success(f.register(UUID.randomUUID(), legacy = legacy))
        val lease = f.providerLease(legacy)
        f.clock.at = Instant.now().truncatedTo(ChronoUnit.MICROS)
        success(f.freeze(UUID.randomUUID(), target.id))
        val foreign = Fixture()
        for (result in
            listOf(
                f.drain(UUID.randomUUID()),
                f.drain(target.id, request = f.actor.copy(companyId = f.company)),
                f.drain(target.id, request = foreign.actor, parent = foreign.session),
                f.drain(target.id, parent = f.parent()),
                f.drain(target.id, nativeVersion = 1),
                f.drain(target.id, deviceVersion = 1),
                f.drain(target.id, requestedSpace = UUID.randomUUID()),
                f.drain(target.id, build = client.copy(build = 27)),
                f.drain(target.id, fingerprint = "b".repeat(64)),
            )) assertInstanceOf(Result.Failed::class.java, result)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val released =
                object : CollaborationLegacyRuntimeRepository by f.runtimes {
                    override fun slots(
                        owner: CollaborationLegacyRuntimeOwner
                    ): Result<List<CollaborationLegacyRuntimeLease>> =
                        f.runtimes.slots(owner).map { original ->
                            assertTrue(
                                pool
                                    .submit<Boolean> {
                                        success(
                                            f.transactions.run(
                                                f.actor.copy(companyId = f.company)
                                            ) {
                                                f.runtimes.release(lease)
                                            }
                                        )
                                    }
                                    .get(5, TimeUnit.SECONDS)
                            )
                            original
                        }
                }
            assertEquals(1, success(f.drain(target.id, runtimes = released)).activeRequests)
            f.clock.at = Instant.now().truncatedTo(ChronoUnit.MICROS)
            assertEquals(0, success(f.drain(target.id)).activeRequests)
            f.freezeCounts(1, 1, 1)
            foreign.freezeCounts(0, 0, 0)
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private inner class Fixture {
        val account = UUID.randomUUID()
        val company = UUID.randomUUID()
        val actor =
            Actor(
                account,
                null,
                emptySet(),
                now.minusSeconds(120),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val clock = MutableClock(now)
        val sql = DSL.using(TransactionAwareDataSourceProxy(runtime), SQLDialect.POSTGRES)
        val completions =
            StoredCollaborationLegacyRuntimeCompletionRepository(
                PostgresCollaborationLegacyRuntimeCompletionDataSource(sql)
            )
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(runtime), JdbcTemplate(runtime))
        val selections =
            StoredCollaborationSpaceIdentityRepository(
                PostgresCollaborationSpaceIdentityDataSource(sql)
            )
        val operations =
            PostgresOperationRepository(PostgresOperationReceiptDataSource(sql), ObjectMapper())
        val journal =
            PostgresChangeJournalRepository(PostgresChangeJournalDataSource(sql, ObjectMapper()))
        val identities =
            StoredIdentityRepository(
                PostgresIdentityDataSource(sql),
                object : PasswordDataSource {
                    override fun hash(password: String): String? =
                        error("Selection must not use passwords")

                    override fun matches(password: String, hash: String): Boolean =
                        error("Selection must not use passwords")
                },
                "unused-fixture-hash",
            )
        val sessions =
            StoredNativeSessionRepository(
                PostgresNativeSessionDataSource(sql),
                object : IdentityTokenDataSource {
                    override fun generate(): String = error("Selection must not acquire tokens")

                    override fun hash(token: String): String =
                        error("Selection must not acquire tokens")

                    override fun encrypt(purpose: String, value: String): String =
                        error("Selection must not acquire tokens")

                    override fun decrypt(purpose: String, value: String): String =
                        error("Selection must not acquire tokens")
                },
            )
        val spaces = StoredCollaborationSpaceRepository(PostgresCollaborationSpaceDataSource(sql))
        val registry =
            StoredCollaborationSpaceDeviceRepository(
                PostgresCollaborationSpaceDeviceDataSource(sql)
            )
        val provisioning =
            StoredCollaborationSpaceProvisioningRepository(
                PostgresCollaborationSpaceProvisioningDataSource(sql)
            )
        val legacyParents =
            StoredCollaborationLegacyParentRepository(
                PostgresCollaborationLegacyParentDataSource(sql)
            )
        val runtimes =
            StoredCollaborationLegacyRuntimeRepository(
                PostgresCollaborationLegacyRuntimeDataSource(sql)
            )
        val session: UUID

        init {
            owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                company,
                compact(company).take(20),
                "Fictional company",
            )
            owner.update(
                "insert into accounts(id,email,display_name) values(?,?,?)",
                account,
                "$account@example.test",
                "Fictional employee",
            )
            owner.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
            owner.update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                company,
                account,
            )
            session = parent()
        }

        fun parent(): UUID {
            val id = UUID.randomUUID()
            owner.update(
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
                id,
                account,
                compact(id).repeat(2),
                actor.authenticatedAt.toString(),
                now.plusSeconds(3600).toString(),
                now.plusSeconds(900).toString(),
                now.minusSeconds(60).toString(),
                now.minusSeconds(30).toString(),
            )
            return id
        }

        fun activeLegacy(): CollaborationLegacyDevicePosition {
            val device = UUID.randomUUID()
            val at = now.minusSeconds(30).toString()
            owner.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                company,
                device,
                account,
                at,
                at,
            )
            owner.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                company,
                device,
                account,
                session,
                at,
            )
            owner.update(
                "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'fixture-envelope-not-a-token',?::timestamptz,?::timestamptz,?::timestamptz+interval '5 minutes')",
                company,
                device,
                account,
                "@hris_${compact(company)}_${compact(account)}:$server",
                "HRIS_${compact(device)}",
                at,
                at,
                at,
            )
            owner.update(
                "update collaboration_devices set state='ACTIVE',version=1 where company_id=? and id=?",
                company,
                device,
            )
            owner.update(
                "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?, ?,0,?::timestamptz)",
                company,
                device,
                account,
                "a".repeat(64),
                at,
            )
            return CollaborationLegacyDevicePosition(company, device)
        }

        fun select() {
            success(
                SelectNativeCollaborationSpaceIdentity(
                        selections,
                        spaces,
                        sessions,
                        identities,
                        operations,
                        journal,
                        transactions,
                        CollaborationSpacePolicy(true, 28, 28),
                        CollaborationMatrixPolicy(true, server),
                        IdentitySecurityPolicy(),
                        clock,
                    )
                    .execute(actor, session, 0, space, client, UUID.randomUUID())
            )
        }

        fun status(
            request: Actor = actor,
            parent: UUID = session,
            version: Long = 0,
            requestedSpace: UUID = space,
            build: ClientVersion? = client,
            registry: CollaborationSpaceDeviceRepository = this.registry,
            provisioning: CollaborationSpaceProvisioningRepository = this.provisioning,
            policy: CollaborationSpacePolicy = CollaborationSpacePolicy(true, 28, 28),
        ): Result<CollaborationSpaceDeviceStatus?> =
            GetNativeCollaborationSpaceDeviceStatus(
                    registry,
                    provisioning,
                    EncryptedCollaborationSpaceGatewaySessionRepository(
                        PostgresCollaborationSpaceGatewaySessionDataSource(sql),
                        JceIdentityTokenDataSource(
                            parseIdentityKeyring(
                                "fixture",
                                "fixture:" +
                                    java.util.Base64.getEncoder()
                                        .encodeToString(ByteArray(32) { 47 }),
                            )
                        ),
                    ),
                    spaces,
                    sessions,
                    identities,
                    transactions,
                    policy,
                    IdentitySecurityPolicy(),
                    clock,
                )
                .execute(request, parent, version, requestedSpace, build)

        fun register(
            operation: UUID,
            request: Actor = actor,
            session: UUID = this.session,
            version: Long = 0,
            build: ClientVersion? = client,
            requestedServer: String = server,
            legacy: CollaborationLegacyDevicePosition? = null,
            fingerprint: String? = if (legacy == null) null else "a".repeat(64),
            registry: CollaborationSpaceDeviceRepository = this.registry,
            provisioning: CollaborationSpaceProvisioningRepository = this.provisioning,
            operations: OperationRepository = this.operations,
            journal: ChangeJournalRepository = this.journal,
        ): Result<MutationReceipt> =
            RegisterNativeCollaborationSpaceDevice(
                    registry,
                    provisioning,
                    legacyParents,
                    completions,
                    selections,
                    spaces,
                    sessions,
                    identities,
                    operations,
                    journal,
                    transactions,
                    CollaborationSpacePolicy(true, 28, 28),
                    CollaborationMatrixPolicy(true, requestedServer),
                    IdentitySecurityPolicy(),
                    clock,
                )
                .execute(
                    request,
                    session,
                    version,
                    space,
                    build,
                    operation,
                    CollaborationPlatform.ANDROID,
                    "Fictional",
                    if (legacy == null) CollaborationSpaceDeviceOrigin.SPACE
                    else CollaborationSpaceDeviceOrigin.LEGACY,
                    legacy,
                    fingerprint,
                )

        fun freeze(
            operation: UUID,
            target: UUID,
            request: Actor = actor,
            parent: UUID = session,
            nativeVersion: Long = 0,
            deviceVersion: Long = 0,
            requestedSpace: UUID = space,
            build: ClientVersion? = client,
            fingerprint: String = "a".repeat(64),
            runtimes: CollaborationLegacyRuntimeRepository = this.runtimes,
            operations: OperationRepository = this.operations,
            journal: ChangeJournalRepository = this.journal,
        ): Result<MutationReceipt> =
            FreezeNativeCollaborationLegacyRuntime(
                    registry,
                    legacyParents,
                    selections,
                    runtimes,
                    provisioning,
                    completions,
                    spaces,
                    sessions,
                    identities,
                    operations,
                    journal,
                    transactions,
                    CollaborationSpacePolicy(true, 28, 28),
                    CollaborationMatrixPolicy(true, server),
                    IdentitySecurityPolicy(),
                    clock,
                )
                .execute(
                    request,
                    parent,
                    nativeVersion,
                    requestedSpace,
                    build,
                    operation,
                    target,
                    deviceVersion,
                    fingerprint,
                )

        fun drain(
            target: UUID,
            request: Actor = actor,
            parent: UUID = session,
            nativeVersion: Long = 0,
            deviceVersion: Long = 0,
            requestedSpace: UUID = space,
            build: ClientVersion? = client,
            fingerprint: String = "a".repeat(64),
            runtimes: CollaborationLegacyRuntimeRepository = this.runtimes,
        ): Result<CollaborationLegacyRuntimeDrainStatus> =
            GetNativeCollaborationLegacyRuntimeDrain(
                    registry,
                    legacyParents,
                    selections,
                    runtimes,
                    provisioning,
                    completions,
                    spaces,
                    sessions,
                    identities,
                    transactions,
                    CollaborationSpacePolicy(true, 28, 28),
                    CollaborationMatrixPolicy(true, server),
                    IdentitySecurityPolicy(),
                    clock,
                )
                .execute(
                    request,
                    parent,
                    nativeVersion,
                    requestedSpace,
                    build,
                    target,
                    deviceVersion,
                    fingerprint,
                )

        fun providerLease(
            legacy: CollaborationLegacyDevicePosition,
            seconds: Long = 45,
        ): CollaborationLegacyRuntimeLease {
            val original =
                CollaborationLegacyRuntimeOwner(legacy.companyId, legacy.deviceId, account, session)
            return requireNotNull(
                success(
                    transactions.run(actor.copy(companyId = company)) {
                        runtimes.ensure(original, now).flatMap {
                            runtimes.claim(
                                original,
                                Instant.now().plusSeconds(seconds).truncatedTo(ChronoUnit.MICROS),
                            )
                        }
                    }
                )
            )
        }

        fun frozen(legacy: CollaborationLegacyDevicePosition): CollaborationLegacyRuntime? =
            success(
                transactions.run(actor) {
                    runtimes.find(
                        CollaborationLegacyRuntimeOwner(
                            legacy.companyId,
                            legacy.deviceId,
                            account,
                            session,
                        ),
                        lock = false,
                    )
                }
            )

        fun freezeCounts(runtimes: Int, receipts: Int, events: Int) {
            assertEquals(
                runtimes,
                owner.queryForObject(
                    "select count(*) from collaboration_legacy_runtime where account_id=?",
                    Int::class.java,
                    account,
                ),
            )
            assertEquals(
                receipts,
                owner.queryForObject(
                    "select count(*) from operation_receipts where actor_id=? and operation='collaboration.legacy_runtime_freeze'",
                    Int::class.java,
                    account,
                ),
            )
            for (table in listOf("audit_entries", "platform_audit_metadata")) assertEquals(
                events,
                owner.queryForObject(
                    "select count(*) from $table where actor_id=? and action='collaboration.legacy_runtime_frozen'",
                    Int::class.java,
                    account,
                ),
            )
            assertEquals(
                events,
                owner.queryForObject(
                    "select count(*) from outbox_events where actor_id=? and event_type='collaboration.legacy_runtime_frozen'",
                    Int::class.java,
                    account,
                ),
            )
        }

        fun device(parent: UUID = session): CollaborationSpaceDevice =
            requireNotNull(
                success(transactions.run(actor) { registry.findBound(space, account, parent) })
            )

        fun work(device: UUID): CollaborationSpaceProvisioning =
            requireNotNull(
                success(transactions.run(actor) { provisioning.metadata(space, account, device) })
            )

        fun counts(devices: Int, receipts: Int, events: Int) {
            assertEquals(
                devices,
                owner.queryForObject(
                    "select count(*) from collaboration_space_provisioning where account_id=?",
                    Int::class.java,
                    account,
                ),
            )
            assertEquals(
                devices,
                owner.queryForObject(
                    "select count(*) from collaboration_space_devices where account_id=?",
                    Int::class.java,
                    account,
                ),
            )
            assertEquals(
                devices,
                owner.queryForObject(
                    "select count(*) from collaboration_space_device_sessions where account_id=?",
                    Int::class.java,
                    account,
                ),
            )
            assertEquals(
                receipts,
                owner.queryForObject(
                    "select count(*) from operation_receipts where actor_id=? and operation='collaboration.space_device_register'",
                    Int::class.java,
                    account,
                ),
            )
            for (table in listOf("audit_entries", "platform_audit_metadata")) assertEquals(
                events,
                owner.queryForObject(
                    "select count(*) from $table where actor_id=? and action='collaboration.space_device_registered'",
                    Int::class.java,
                    account,
                ),
            )
            assertEquals(
                events,
                owner.queryForObject(
                    "select count(*) from outbox_events where actor_id=? and event_type='collaboration.space_device_registered'",
                    Int::class.java,
                    account,
                ),
            )
        }
    }

    private fun hookJournal(
        action: (Actor, ChangeRecord) -> Result<Unit>
    ): ChangeJournalRepository =
        object : ChangeJournalRepository {
            override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                action(actor, change)
        }

    private class MutableClock(var at: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = at
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result) {
            if (result is Result.Failed) result.failure.code else "Unexpected result"
        }
        return (result as Result.Success).value
    }

    private fun failure(result: Result<*>, code: String) {
        assertInstanceOf(Result.Failed::class.java, result)
        assertEquals(code, (result as Result.Failed).failure.code)
    }
}
