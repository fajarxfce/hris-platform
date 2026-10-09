package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.identity.domain.usecases.*
import dev.fajar.hris.mail.data.datasources.JakartaMailDataSource
import dev.fajar.hris.mail.data.di.*
import dev.fajar.hris.mail.data.repositories.SmtpMailRepository
import dev.fajar.hris.mail.domain.entities.OutboundMail
import dev.fajar.hris.mail.domain.repositories.MailRepository
import dev.fajar.hris.worker.mail.*
import dev.fajar.hris.worker.runtime.DeadlineTask
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import java.io.ByteArrayInputStream
import java.time.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties =
        [
            "hris.worker.enabled=false",
            "HRIS_MAIL_ENABLED=true",
            "HRIS_MAIL_HOST=127.0.0.1",
            "HRIS_MAIL_PORT=1",
            "HRIS_MAIL_FROM=no-reply@example.test",
            "HRIS_MAIL_TRANSPORT=PLAINTEXT",
            "HRIS_MAIL_ALLOW_LOOPBACK_PLAINTEXT=true",
            "HRIS_PUBLIC_URL=https://hris.example.test",
        ],
)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
)
class IdentityMailWorkerTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.6-alpine").withInitScript("worker-role.sql")

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { "hris_worker_test" }
            registry.add("spring.datasource.password") { "worker-fixture-only" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { postgres.username }
            registry.add("spring.flyway.password") { postgres.password }
            registry.add("HRIS_IDENTITY_KEYS") {
                "v1:" + Base64.getEncoder().encodeToString(ByteArray(32) { 23 })
            }
        }
    }

    @Autowired private lateinit var credentials: CredentialChallengeRepository
    @Autowired private lateinit var deliveries: IdentityMailRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var lease: LeaseIdentityMail
    @Autowired private lateinit var policy: CredentialChallengePolicy
    @Autowired private lateinit var clock: Clock
    private val accounts = mutableListOf<UUID>()

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    private fun actor() = Actor(UUID(0, 0), null, emptySet(), clock.instant(), UUID.randomUUID())

    private fun fixture(expired: Boolean = false): UUID {
        val account = UUID.randomUUID()
        accounts += account
        database()
            .update(
                "insert into accounts(id,email,display_name,active,invitation_pending) values(?,?,'Mail fixture',false,true)",
                account,
                "$account@example.test",
            )
        val now = clock.instant()
        val id = UUID.randomUUID()
        val challenge =
            CredentialChallenge(
                id,
                account,
                CredentialChallengeKind.INVITATION,
                0,
                UUID(0, 0),
                now.minusSeconds(60),
                if (expired) now.minusSeconds(1) else now.plusSeconds(1800),
                null,
                null,
            )
        assertEquals(
            Result.Success(Unit),
            transactions.run(actor()) {
                credentials.issue(challenge).flatMap { deliveries.enqueue(it) }
            },
        )
        return id
    }

    private fun claim(): IdentityMailLease {
        val result = lease.execute(UUID.randomUUID(), 1)
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value.single()
    }

    private fun deliver(mail: MailRepository) =
        DeliverIdentityMail(deliveries, credentials, mail, journal, transactions, policy, clock)

    private fun state(id: UUID) =
        database()
            .queryForObject(
                "select state from identity_mail_deliveries where challenge_id=?",
                String::class.java,
                id,
            )

    @AfterEach
    fun settle() {
        accounts.forEach {
            database()
                .update(
                    """update identity_mail_deliveries set state='SUPERSEDED',token_encrypted=null,lease_owner=null,lease_token=null,lease_until=null
            where account_id=? and state in ('PENDING','LEASED')""",
                    it,
                )
        }
    }

    @Test
    fun sendsActualSmtpAfterCommitAndDiscardsCiphertextAfterFencedAcknowledgement() {
        val id = fixture()
        val owned = claim()
        SmtpFixture().use { smtp ->
            val outbound =
                SmtpMailRepository(
                    JakartaMailDataSource(
                        createSmtpSender(
                            SmtpSettings(
                                "127.0.0.1",
                                smtp.port,
                                "no-reply@example.test",
                                "",
                                "",
                                "PLAINTEXT",
                                true,
                            )
                        ),
                        "no-reply@example.test",
                    )
                )
            assertEquals(
                Result.Success(Unit),
                deliver(
                        MailRepository { message ->
                            assertFalse(
                                TransactionSynchronizationManager.isActualTransactionActive()
                            )
                            outbound.send(message)
                        }
                    )
                    .execute(owned),
            )
            val mime =
                MimeMessage(
                    Session.getInstance(Properties()),
                    ByteArrayInputStream(
                        smtp.message.get(3, TimeUnit.SECONDS).toByteArray(Charsets.US_ASCII)
                    ),
                )
            assertEquals("<$id@example.test>", mime.messageID)
            assertTrue(
                mime.content
                    .toString()
                    .contains("https://hris.example.test/auth/accept-invitation#token=")
            )
            assertFalse(mime.content.toString().contains("?token="))
            smtp.awaitConnectionClosed()
        }
        assertEquals("SENT", state(id))
        assertNull(
            database()
                .queryForObject(
                    "select token_encrypted from identity_mail_deliveries where challenge_id=?",
                    String::class.java,
                    id,
                )
        )
        assertEquals(
            "mail_lease_lost",
            (deliver(MailRepository { fail("Must not send twice after acknowledgement") })
                    .execute(owned) as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun expiredRevokedAndObsoleteLeasesNeverReachSmtp() {
        val expired = fixture(expired = true)
        assertEquals(
            Result.Success(emptyList<IdentityMailLease>()),
            lease.execute(UUID.randomUUID(), 1),
        )
        assertEquals("FAILED", state(expired))
        val id = fixture()
        val first = claim()
        assertEquals(
            "mail_lease_lost",
            (deliver(MailRepository { fail("Wrong lease") })
                    .execute(first.copy(token = UUID.randomUUID())) as Result.Failed)
                .failure
                .code,
        )
        database()
            .update(
                "update accounts set security_version=security_version+1 where id=?",
                first.accountId,
            )
        assertEquals(
            Result.Success(Unit),
            deliver(MailRepository { fail("Revoked link") }).execute(first),
        )
        assertEquals("SUPERSEDED", state(id))
    }

    @Test
    fun transientDeliveryIsDeferredButPermanentErrorsAndAttemptExhaustionAreTerminal() {
        val id = fixture()
        val first = claim()
        val retry =
            deliver(
                MailRepository {
                    Result.Failed(Failure(FailureKind.UNAVAILABLE, "mail_temporarily_unavailable"))
                }
            )
        assertEquals(Result.Success(Unit), retry.execute(first))
        assertEquals("PENDING", state(id))
        assertEquals(
            Result.Success(emptyList<IdentityMailLease>()),
            lease.execute(UUID.randomUUID(), 1),
        )
        database()
            .update(
                "update identity_mail_deliveries set available_at=now()-interval '1 second' where challenge_id=?",
                id,
            )
        val second = claim()
        assertEquals(2, second.attempts)
        assertEquals(
            Result.Success(Unit),
            deliver(
                    MailRepository {
                        Result.Failed(Failure(FailureKind.VALIDATION, "mail_recipient_rejected"))
                    }
                )
                .execute(second),
        )
        assertEquals("FAILED", state(id))
        val exhausted = fixture()
        val last = claim()
        database()
            .update(
                "update identity_mail_deliveries set attempts=8,lease_until=now()-interval '1 second' where challenge_id=?",
                exhausted,
            )
        assertEquals(
            Result.Success(emptyList<IdentityMailLease>()),
            lease.execute(UUID.randomUUID(), 1),
        )
        assertEquals("FAILED", state(exhausted))
        assertEquals(
            "mail_lease_lost",
            (deliver(MailRepository { fail("Exhausted delivery") }).execute(last) as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun competingClaimsAndReclamationPreserveFencing() {
        val id = fixture()
        val start = CountDownLatch(1)
        val ready = CountDownLatch(2)
        val claims =
            Executors.newFixedThreadPool(2).use { pool ->
                val tasks =
                    (1..2).map {
                        pool.submit<Result<List<IdentityMailLease>>> {
                            ready.countDown()
                            check(start.await(5, TimeUnit.SECONDS))
                            lease.execute(UUID.randomUUID(), 1)
                        }
                    }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                tasks.flatMap { (it.get(10, TimeUnit.SECONDS) as Result.Success).value }
            }
        assertEquals(1, claims.size)
        val first = claims.single()
        database()
            .update(
                "update identity_mail_deliveries set lease_until=now()-interval '1 second' where challenge_id=?",
                id,
            )
        val next = claim()
        assertNotEquals(first.token, next.token)
        val delivered = AtomicInteger()
        val handler =
            deliver(
                MailRepository {
                    delivered.incrementAndGet()
                    Result.Success(Unit)
                }
            )
        assertTrue(handler.execute(first) is Result.Failed)
        assertEquals(0, delivered.get())
        assertEquals(Result.Success(Unit), handler.execute(next))
        assertEquals(1, delivered.get())
    }

    @Test
    fun lostAcknowledgementCanRepeatDeliveryWithOneStableMessageAndOneCommittedAudit() {
        val id = fixture()
        val owned = claim()
        val messages = mutableListOf<OutboundMail>()
        val handler =
            deliver(
                MailRepository {
                    messages += it
                    Result.Success(Unit)
                }
            )
        database()
            .execute(
                """create function fail_mail_ack() returns trigger language plpgsql as ${'$'}${'$'} begin
            if new.resource_id='$id'::uuid and new.action='identity.mail_sent' then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger mail_ack_probe before insert on audit_entries for each row execute function fail_mail_ack()"
            )
        try {
            assertTrue(handler.execute(owned) is Result.Failed)
            assertEquals("LEASED", state(id))
        } finally {
            database().execute("drop trigger mail_ack_probe on audit_entries")
            database().execute("drop function fail_mail_ack()")
        }
        assertEquals(Result.Success(Unit), handler.execute(owned))
        assertEquals(2, messages.size)
        assertEquals(messages[0], messages[1])
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='identity.mail_sent'",
                    Int::class.java,
                    id,
                ),
        )
    }

    @Test
    fun workerDeadlineAndShutdownReleaseBlockedDeliveriesAndOwnedThreads() {
        for (timeout in listOf(true, false)) {
            fixture()
            val entered = CountDownLatch(1)
            val interrupted = CountDownLatch(1)
            val blocking =
                deliver(
                    MailRepository {
                        entered.countDown()
                        try {
                            check(CountDownLatch(1).await(10, TimeUnit.SECONDS))
                            Result.Success(Unit)
                        } catch (error: InterruptedException) {
                            interrupted.countDown()
                            throw error
                        }
                    }
                )
            val tasks =
                ThreadPoolTaskExecutor().apply {
                    corePoolSize = 2
                    maxPoolSize = 2
                    setQueueCapacity(0)
                    setThreadNamePrefix("mail-test-task-")
                    setAwaitTerminationSeconds(2)
                    initialize()
                }
            val timers =
                ThreadPoolTaskScheduler().apply {
                    poolSize = 2
                    setThreadNamePrefix("mail-test-timer-")
                    setRemoveOnCancelPolicy(true)
                    setAwaitTerminationSeconds(2)
                    initialize()
                }
            val scheduler =
                IdentityMailWorker(
                    lease,
                    blocking,
                    tasks,
                    timers,
                    IdentityMailWorkerSettings(
                        Duration.ofMillis(10),
                        if (timeout) Duration.ofMillis(500) else Duration.ofSeconds(10),
                    ),
                )
            scheduler.start()
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                if (timeout) assertTrue(interrupted.await(3, TimeUnit.SECONDS))
            } finally {
                scheduler.stop()
            }
            assertTrue(interrupted.await(2, TimeUnit.SECONDS))
            assertFalse(scheduler.isRunning)
            assertTrue(tasks.threadPoolExecutor.isTerminated)
            assertTrue(timers.scheduledThreadPoolExecutor.isTerminated)
            assertTrue(timers.scheduledThreadPoolExecutor.queue.isEmpty())
            assertThrows(IllegalStateException::class.java) { scheduler.start() }
            settle()
        }
        assertTrue(
            Thread.getAllStackTraces().keys.none {
                it.isAlive &&
                    (it.name.startsWith("mail-test-task-") ||
                        it.name.startsWith("mail-test-timer-"))
            }
        )
    }

    @Test
    fun completedTasksCancelTheirTimersAndLateCancellationDoesNotInterruptReusedThreads() {
        val timers = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val completed = DeadlineTask {}
                completed.watch(timers.schedule({ completed.cancel(true) }, 1, TimeUnit.HOURS))
                executor.execute(completed)
                completed.get(3, TimeUnit.SECONDS)
                executor.submit {}.get(3, TimeUnit.SECONDS)
                assertTrue(timers.queue.isEmpty())
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                val next =
                    executor.submit<Boolean> {
                        entered.countDown()
                        release.await(3, TimeUnit.SECONDS) && !Thread.currentThread().isInterrupted
                    }
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                assertFalse(completed.cancel(true))
                release.countDown()
                assertTrue(next.get(3, TimeUnit.SECONDS))
                val cancelled = DeadlineTask {}
                cancelled.cancel(true)
                cancelled.watch(timers.schedule({ cancelled.cancel(true) }, 1, TimeUnit.HOURS))
                assertTrue(timers.queue.isEmpty())
            }
        } finally {
            timers.shutdownNow()
            assertTrue(timers.awaitTermination(3, TimeUnit.SECONDS))
        }
    }
}
