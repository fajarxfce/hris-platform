package dev.fajar.hris.worker

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.InboxPushRepository
import dev.fajar.hris.communications.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.usecases.SaveNativePushRegistration
import dev.fajar.hris.push.domain.entities.*
import dev.fajar.hris.worker.push.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Import(InboxPushProbeConfiguration::class)
class InboxPushWorkerTest : CommunicationsWorkerFixture() {
    @Autowired private lateinit var leasePush: LeaseInboxPush
    @Autowired private lateinit var deliver: DeliverInboxPush
    @Autowired private lateinit var registrations: SaveNativePushRegistration
    @Autowired private lateinit var archive: ArchiveAnnouncement
    @Autowired private lateinit var probe: InboxPushProbe
    @Autowired private lateinit var dispatches: InboxPushRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var journal: ChangeJournalRepository

    private data class Device(
        val id: UUID,
        val account: UUID,
        val actor: Actor,
        val token: String,
    ) {
        override fun toString() = "Device(<redacted>)"
    }

    @BeforeEach
    fun resetProbe() {
        probe.clear()
    }

    @AfterEach
    fun clearProbe() {
        probe.clear()
    }

    private fun recipient(actor: Actor): UUID =
        requireNotNull(
            database()
                .queryForObject(
                    "select account_id from membership_permissions where company_id=? and permission='announcements.read' and account_id<>?",
                    UUID::class.java,
                    actor.companyId,
                    actor.accountId,
                )
        )

    private fun device(actor: Actor, id: UUID = UUID.randomUUID()): Device {
        val account = recipient(actor)
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val credential =
            requireNotNull(
                database()
                    .queryForObject(
                        "select security_version from accounts where id=?",
                        Long::class.java,
                        account,
                    )
            )
        database()
            .update(
                """insert into native_sessions(id,account_id,refresh_hash,access_hash,authenticated_at,created_at,expires_at,access_expires_at,credential_version)
            values(?,?,?, ?,?,?,?, ?,?)""",
                id,
                account,
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now.plusSeconds(86400 * 30)),
                java.sql.Timestamp.from(now.plusSeconds(600)),
                credential,
            )
        val owner =
            Actor(account, null, emptySet(), now, UUID.randomUUID(), credentialVersion = credential)
        val token = "fixture-push:$id"
        val saved =
            registrations.execute(
                owner,
                id,
                0,
                UUID.randomUUID(),
                SaveNativePushRegistrationCommand(null, PushPlatform.ANDROID, token),
            )
        assertTrue(saved is Result.Success, saved.toString())
        return Device(id, account, owner, token)
    }

    private fun publish(actor: Actor): UUID {
        val lease = queue(actor)
        assertEquals(Result.Success(Unit), executor.execute(lease, task))
        return requireNotNull(
            database()
                .queryForObject(
                    "select id from inbox_items where company_id=? and publication_id=?",
                    UUID::class.java,
                    actor.companyId,
                    lease.job.request.id,
                )
        )
    }

    private fun claim(): InboxPushLease {
        val result = leasePush.execute(UUID.randomUUID(), 1)
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value.single()
    }

    private fun row(inbox: UUID) =
        database().queryForMap("select * from inbox_push_dispatches where inbox_id=?", inbox)

    private fun finish(inbox: UUID, maximumSteps: Int = 8) {
        repeat(maximumSteps) {
            if (row(inbox)["state"] in setOf("COMPLETE", "FAILED", "SUPERSEDED")) return
            assertEquals(Result.Success(Unit), deliver.execute(claim()))
        }
        fail<Unit>("Dispatch did not reach a terminal state within its expected step bound")
    }

    private fun due(inbox: UUID) {
        database()
            .update("update inbox_push_dispatches set available_at=now() where inbox_id=?", inbox)
    }

    private fun expireLease(inbox: UUID) {
        database()
            .update(
                "update inbox_push_dispatches set lease_until=now()-interval '1 second' where inbox_id=?",
                inbox,
            )
    }

    @Test
    fun publishedInboxCreatesDurableCheckpointsAndOnlyPreexistingDevicesReceiveHints() {
        val actor = fixture()
        val first = device(actor)
        val second = device(actor)
        val inbox = publish(actor)
        assertEquals("PENDING", row(inbox)["state"])
        assertTrue(probe.messages.isEmpty())
        val late = device(actor)
        val initial = claim()
        assertEquals(Result.Success(Unit), deliver.execute(initial))
        assertEquals(1, row(inbox)["processed_count"])
        val resumed = claim()
        assertNotEquals(initial.owner, resumed.owner)
        assertEquals(Result.Success(Unit), deliver.execute(resumed))
        finish(inbox)
        assertEquals("COMPLETE", row(inbox)["state"])
        assertEquals(2, row(inbox)["accepted_count"])
        assertEquals(2, row(inbox)["processed_count"])
        assertEquals(setOf(first.token, second.token), probe.messages.map { it.token }.toSet())
        assertFalse(probe.messages.any { it.token == late.token })
        assertEquals(setOf(inbox.toString()), probe.messages.map { it.data["eventId"] }.toSet())
        assertTrue(
            probe.messages.all {
                it.data.keys ==
                    setOf(
                        "schemaVersion",
                        "eventCode",
                        "eventId",
                        "accountId",
                        "companyId",
                        "inboxId",
                    )
            }
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='communications.push_complete'",
                    Int::class.java,
                    inbox,
                ),
        )
        assertEquals("push_lease_lost", (deliver.execute(initial) as Result.Failed).failure.code)
    }

    @Test
    fun retryUsesPersistedTargetBackoffAndANewFencedLease() {
        val actor = fixture()
        device(actor)
        val inbox = publish(actor)
        probe.send.set {
            Result.Failed(
                Failure(
                    FailureKind.RATE_LIMITED,
                    "push_rate_limited",
                    parameters = mapOf("retryAfterSeconds" to "60"),
                )
            )
        }
        val initial = claim()
        assertEquals(Result.Success(Unit), deliver.execute(initial))
        val failed = row(inbox)
        assertEquals("PENDING", failed["state"])
        assertNotNull(failed["target_session_id"])
        assertEquals(1, failed["attempts"])
        assertEquals(0, failed["processed_count"])
        val available = (failed["available_at"] as java.sql.Timestamp).toInstant()
        assertTrue(available.isAfter(Instant.now().plusSeconds(50)))
        assertEquals(
            Result.Success(emptyList<InboxPushLease>()),
            leasePush.execute(UUID.randomUUID(), 1),
        )
        due(inbox)
        val renewed = claim()
        assertNotEquals(initial.token, renewed.token)
        assertEquals("push_lease_lost", (deliver.execute(initial) as Result.Failed).failure.code)
        probe.send.set { Result.Success(PushOutcome.ACCEPTED) }
        assertEquals(Result.Success(Unit), deliver.execute(renewed))
        finish(inbox)
        assertEquals(1, row(inbox)["accepted_count"])
        assertEquals(2, probe.messages.size)
        assertEquals(1, probe.messages.map { it.data["eventId"] }.toSet().size)
    }

    @Test
    fun unregisteredTokenRetirementCannotEraseANewerTokenAndRollsBackIfAcknowledgementFails() {
        val actor = fixture()
        val device = device(actor)
        val inbox = publish(actor)
        val replacement = "replacement:${UUID.randomUUID()}"
        probe.send.set {
            assertTrue(
                registrations.execute(
                    device.actor,
                    device.id,
                    0,
                    UUID.randomUUID(),
                    SaveNativePushRegistrationCommand(0, PushPlatform.ANDROID, replacement),
                ) is Result.Success
            )
            Result.Success(PushOutcome.UNREGISTERED)
        }
        assertEquals(Result.Success(Unit), deliver.execute(claim()))
        assertEquals(
            true,
            database()
                .queryForObject(
                    "select enabled from native_push_registrations where session_id=?",
                    Boolean::class.java,
                    device.id,
                ),
        )
        assertEquals(
            1L,
            database()
                .queryForObject(
                    "select version from native_push_registrations where session_id=?",
                    Long::class.java,
                    device.id,
                ),
        )
        finish(inbox)
        val second = publish(actor)
        probe.send.set { Result.Success(PushOutcome.UNREGISTERED) }
        probe.failAdvance.set(true)
        val owned = claim()
        assertEquals("push_lease_lost", (deliver.execute(owned) as Result.Failed).failure.code)
        assertEquals(
            true,
            database()
                .queryForObject(
                    "select enabled from native_push_registrations where session_id=?",
                    Boolean::class.java,
                    device.id,
                ),
        )
        assertEquals("LEASED", row(second)["state"])
        probe.failAdvance.set(false)
        expireLease(second)
        assertEquals(Result.Success(Unit), deliver.execute(claim()))
        val retired =
            database()
                .queryForMap(
                    "select enabled,token_hash,token_encrypted,version from native_push_registrations where session_id=?",
                    device.id,
                )
        assertEquals(false, retired["enabled"])
        assertNull(retired["token_hash"])
        assertNull(retired["token_encrypted"])
        assertEquals(2L, retired["version"])
        finish(second)
        assertEquals(1, row(second)["rejected_count"])
    }

    @Test
    fun revokedCredentialsOrWithdrawnInboxPreventNewProviderCalls() {
        val actor = fixture()
        val device = device(actor)
        val inbox = publish(actor)
        val barrier = InboxPushProbe.Barrier(device.account)
        probe.barrier.set(barrier)
        val lease = claim()
        Executors.newSingleThreadExecutor().use { executor ->
            val pending = executor.submit<Result<Unit>> { deliver.execute(lease) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        device.account,
                    )
            } finally {
                barrier.release.countDown()
            }
            assertEquals(Result.Success(Unit), pending.get(10, TimeUnit.SECONDS))
        }
        probe.barrier.set(null)
        finish(inbox)
        assertTrue(probe.messages.isEmpty())
        val other = fixture()
        device(other)
        val withdrawn = publish(other)
        val announcement =
            requireNotNull(
                database()
                    .queryForObject(
                        "select announcement_id from inbox_items where id=?",
                        UUID::class.java,
                        withdrawn,
                    )
            )
        assertTrue(
            archive.execute(other, UUID.randomUUID(), announcement, 2, "Withdraw publication")
                is Result.Success
        )
        finish(withdrawn)
        assertEquals("SUPERSEDED", row(withdrawn)["state"])
        assertTrue(probe.messages.isEmpty())
    }

    @Test
    fun terminalFailureRollbackAndCrashedLeaseExhaustionRemainRecoverableAndBounded() {
        val actor = fixture()
        val inbox = publish(actor)
        val owned = claim()
        database()
            .execute(
                """CREATE FUNCTION block_push_completion() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.resource_id='$inbox'::uuid AND NEW.action='communications.push_complete' THEN RAISE EXCEPTION 'fixture'; END IF; RETURN NEW; END $$"""
            )
        database()
            .execute(
                "CREATE TRIGGER block_push_completion BEFORE INSERT ON audit_entries FOR EACH ROW EXECUTE FUNCTION block_push_completion()"
            )
        try {
            assertTrue(deliver.execute(owned) is Result.Failed)
            assertEquals("LEASED", row(inbox)["state"])
        } finally {
            database().execute("DROP TRIGGER block_push_completion ON audit_entries")
            database().execute("DROP FUNCTION block_push_completion()")
        }
        expireLease(inbox)
        repeat(7) {
            claim()
            expireLease(inbox)
        }
        assertEquals(
            Result.Success(emptyList<InboxPushLease>()),
            leasePush.execute(UUID.randomUUID(), 1),
        )
        assertEquals("FAILED", row(inbox)["state"])
        assertEquals("push_attempts_exhausted", row(inbox)["failure_code"])
        assertEquals(8, row(inbox)["attempts"])
        assertTrue(probe.messages.isEmpty())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='communications.push_failed'",
                    Int::class.java,
                    inbox,
                ),
        )
    }

    @Test
    fun maintenanceCannotFailOrAuditAStaleCheckpointAfterDeliveryProgress() {
        val actor = fixture()
        device(actor)
        val inbox = publish(actor)
        val future = Instant.now().plusSeconds(2 * 86400)
        val system = Actor(UUID(0, 0), null, emptySet(), future, UUID.randomUUID())
        val selected =
            transactions.run(system) { dispatches.exhausted(future.minusSeconds(86400), 8, 100) }
        assertTrue(selected is Result.Success, selected.toString())
        val observed = (selected as Result.Success).value.single()
        assertEquals(Result.Success(Unit), deliver.execute(claim()))
        assertEquals(1, row(inbox)["accepted_count"])
        assertEquals(
            Result.Success(false),
            transactions.run(system.copy(companyId = actor.companyId)) {
                dispatches.failUnleased(observed, "push_delivery_expired", future)
            },
        )
        assertEquals("PENDING", row(inbox)["state"])
        val cleanup =
            LeaseInboxPush(
                dispatches,
                transactions,
                journal,
                InboxPushPolicy(false),
                Clock.fixed(future, ZoneOffset.UTC),
            )
        assertEquals(
            Result.Success(emptyList<InboxPushLease>()),
            cleanup.execute(UUID.randomUUID(), 1),
        )
        assertEquals("FAILED", row(inbox)["state"])
        assertEquals(1, row(inbox)["accepted_count"])
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='communications.push_failed'",
                    Int::class.java,
                    inbox,
                ),
        )
    }

    @Test
    fun disabledDeliveryStillExpiresAndPurgesOperationalRowsWithoutDeletingInbox() {
        val actor = fixture()
        device(actor)
        val inbox = publish(actor)
        val future = Instant.now().plusSeconds(2 * 86400)
        val cleanup =
            LeaseInboxPush(
                dispatches,
                transactions,
                journal,
                InboxPushPolicy(false),
                Clock.fixed(future, ZoneOffset.UTC),
            )
        assertEquals(
            Result.Success(emptyList<InboxPushLease>()),
            cleanup.execute(UUID.randomUUID(), 1),
        )
        assertEquals("push_delivery_expired", row(inbox)["failure_code"])
        val retention =
            LeaseInboxPush(
                dispatches,
                transactions,
                journal,
                InboxPushPolicy(false),
                Clock.fixed(future.plusSeconds(8 * 86400), ZoneOffset.UTC),
            )
        assertEquals(
            Result.Success(emptyList<InboxPushLease>()),
            retention.execute(UUID.randomUUID(), 1),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from inbox_push_dispatches where inbox_id=?",
                    Int::class.java,
                    inbox,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from inbox_items where id=?",
                    Int::class.java,
                    inbox,
                ),
        )
        assertTrue(probe.messages.isEmpty())
    }

    @Test
    fun workerShutdownInterruptsDeliveryAndReleasesItsOwnedExecutors() {
        val actor = fixture()
        device(actor)
        val inbox = publish(actor)
        val entered = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val release = CountDownLatch(1)
        probe.send.set {
            entered.countDown()
            try {
                check(release.await(5, TimeUnit.SECONDS))
                Result.Success(PushOutcome.ACCEPTED)
            } catch (error: InterruptedException) {
                interrupted.countDown()
                throw error
            }
        }
        val tasks =
            ThreadPoolTaskExecutor().apply {
                corePoolSize = 1
                maxPoolSize = 1
                setQueueCapacity(0)
                setThreadNamePrefix("push-fixture-")
                setWaitForTasksToCompleteOnShutdown(false)
                setAwaitTerminationSeconds(5)
                initialize()
            }
        val timers =
            ThreadPoolTaskScheduler().apply {
                poolSize = 2
                setRemoveOnCancelPolicy(true)
                setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
                setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
                setAwaitTerminationSeconds(5)
                initialize()
            }
        val worker =
            InboxPushWorker(
                leasePush,
                deliver,
                tasks,
                timers,
                InboxPushWorkerSettings(1, Duration.ofMillis(25), Duration.ofSeconds(2)),
            )
        try {
            worker.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            worker.stop()
            assertTrue(interrupted.await(2, TimeUnit.SECONDS))
            assertFalse(worker.isRunning)
            assertTrue(tasks.threadPoolExecutor.isTerminated)
            assertTrue(timers.scheduledThreadPoolExecutor.isTerminated)
            assertEquals("LEASED", row(inbox)["state"])
            assertEquals(0, row(inbox)["accepted_count"])
        } finally {
            release.countDown()
            worker.stop()
        }
        probe.send.set { Result.Success(PushOutcome.ACCEPTED) }
        expireLease(inbox)
        finish(inbox)
        assertEquals("COMPLETE", row(inbox)["state"])
        assertEquals(1, row(inbox)["accepted_count"])
    }
}
