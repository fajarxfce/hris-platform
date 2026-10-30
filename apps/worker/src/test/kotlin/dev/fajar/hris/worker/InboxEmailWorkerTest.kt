package dev.fajar.hris.worker

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.InboxEmailRepository
import dev.fajar.hris.communications.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.worker.mail.*
import java.time.*
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

@Import(InboxEmailProbeConfiguration::class)
class InboxEmailWorkerTest : CommunicationsWorkerFixture() {
    @Autowired private lateinit var leaseEmail: LeaseInboxEmail
    @Autowired private lateinit var deliver: DeliverInboxEmail
    @Autowired private lateinit var archive: ArchiveAnnouncement
    @Autowired private lateinit var probe: InboxEmailProbe
    @Autowired private lateinit var dispatches: InboxEmailRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var journal: ChangeJournalRepository

    @BeforeEach fun resetProbe() = probe.clear()

    @AfterEach fun clearProbe() = probe.clear()

    private fun publish(actor: Actor): UUID {
        val lease = queue(actor, email = true)
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

    private fun claim(): InboxEmailLease {
        val result = leaseEmail.execute(UUID.randomUUID(), 1)
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value.single()
    }

    private fun row(inbox: UUID) =
        database().queryForMap("select * from inbox_email_dispatches where inbox_id=?", inbox)

    private fun finish(inbox: UUID) {
        assertEquals(Result.Success(Unit), deliver.execute(claim()))
        assertTrue(row(inbox)["state"] in setOf("ACCEPTED", "FAILED", "SUPERSEDED"))
    }

    private fun expireLease(inbox: UUID) {
        database()
            .update(
                "update inbox_email_dispatches set lease_until=now()-interval '1 second' where inbox_id=?",
                inbox,
            )
    }

    @Test
    fun acceptedMailUsesCurrentAddressStableIdentityAndOnlyAnAuthorizedInboxLink() {
        val actor = fixture()
        val inbox = publish(actor)
        val account = row(inbox)["account_id"] as UUID
        val initial = claim()
        assertEquals(Result.Success(Unit), deliver.execute(initial))
        assertEquals("ACCEPTED", row(inbox)["state"])
        assertEquals(
            "ACCEPTED",
            database()
                .queryForObject(
                    "select state from announcement_email_delivery where inbox_id=?",
                    String::class.java,
                    inbox,
                ),
        )
        val message = probe.messages.single()
        assertEquals(inbox, message.id)
        assertEquals("$account@example.test", message.recipient)
        assertEquals("New HRIS announcement", message.subject)
        assertTrue(
            message.text.contains(
                "https://hris.example.test/app/dev/accounts/$account/companies/${actor.companyId}/inbox/$inbox"
            )
        )
        assertFalse(message.text.contains("Office update"))
        assertFalse(message.text.contains("Please review"))
        assertFalse(message.toString().contains(message.recipient))
        assertEquals("email_lease_lost", (deliver.execute(initial) as Result.Failed).failure.code)
        assertEquals(1, probe.messages.size)
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='communications.email_accepted'",
                    Int::class.java,
                    inbox,
                ),
        )
        val evidence =
            database()
                .queryForMap("select read_at,acknowledged_at from inbox_items where id=?", inbox)
        assertNull(evidence["read_at"])
        assertNull(evidence["acknowledged_at"])
    }

    @Test
    fun transientFailureWaitsAndUsesAFreshLeaseWhilePermanentFailureIsTerminal() {
        val actor = fixture()
        val inbox = publish(actor)
        probe.send.set {
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "mail_delivery_unavailable"))
        }
        val initial = claim()
        assertEquals(Result.Success(Unit), deliver.execute(initial))
        assertEquals("PENDING", row(inbox)["state"])
        assertTrue(
            (row(inbox)["available_at"] as java.sql.Timestamp)
                .toInstant()
                .isAfter(Instant.now().plusSeconds(3))
        )
        assertEquals(
            Result.Success(emptyList<InboxEmailLease>()),
            leaseEmail.execute(UUID.randomUUID(), 1),
        )
        database()
            .update("update inbox_email_dispatches set available_at=now() where inbox_id=?", inbox)
        val renewed = claim()
        assertNotEquals(initial.token, renewed.token)
        assertEquals("email_lease_lost", (deliver.execute(initial) as Result.Failed).failure.code)
        probe.send.set { Result.Failed(Failure(FailureKind.UNEXPECTED, "mail_delivery_rejected")) }
        assertEquals(Result.Success(Unit), deliver.execute(renewed))
        assertEquals("FAILED", row(inbox)["state"])
        assertEquals("mail_delivery_rejected", row(inbox)["failure_code"])
        assertEquals(2, row(inbox)["attempts"])
        assertEquals(1, probe.messages.map { it.id }.toSet().size)
        assertEquals(
            Result.Success(emptyList<InboxEmailLease>()),
            leaseEmail.execute(UUID.randomUUID(), 1),
        )
    }

    @Test
    fun revokedAccessOrArchivedPublicationPreventsAnotherProviderCall() {
        for (kind in listOf("account", "membership", "company", "permission", "archive")) {
            val actor = fixture()
            val inbox = publish(actor)
            val account = row(inbox)["account_id"]
            val lease = claim()
            when (kind) {
                "account" ->
                    database().update("update accounts set active=false where id=?", account)
                "membership" ->
                    database()
                        .update(
                            "update company_memberships set active=false where company_id=? and account_id=?",
                            actor.companyId,
                            account,
                        )
                "company" ->
                    database()
                        .update("update companies set active=false where id=?", actor.companyId)
                "permission" ->
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='announcements.read'",
                            actor.companyId,
                            account,
                        )
                else -> {
                    val id =
                        database()
                            .queryForObject(
                                "select announcement_id from inbox_items where id=?",
                                UUID::class.java,
                                inbox,
                            )!!
                    assertTrue(
                        archive.execute(actor, UUID.randomUUID(), id, 2, "Withdraw publication")
                            is Result.Success
                    )
                }
            }
            assertEquals(Result.Success(Unit), deliver.execute(lease))
            assertEquals("SUPERSEDED", row(inbox)["state"])
        }
        assertTrue(probe.messages.isEmpty())
    }

    @Test
    fun lostSmtpAcknowledgementRepeatsTheMessageIdButNeverAcceptsAnExpiredLease() {
        val actor = fixture()
        val inbox = publish(actor)
        probe.send.set {
            expireLease(inbox)
            Result.Success(Unit)
        }
        val lease = claim()
        assertEquals("email_lease_lost", (deliver.execute(lease) as Result.Failed).failure.code)
        assertEquals("LEASED", row(inbox)["state"])
        probe.send.set { Result.Success(Unit) }
        finish(inbox)
        assertEquals(2, probe.messages.size)
        assertEquals(1, probe.messages.map { it.id }.toSet().size)
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='communications.email_accepted'",
                    Int::class.java,
                    inbox,
                ),
        )
    }

    @Test
    fun acceptedStateAndProjectionRollBackWithAuditFailureAndCrashedClaimsExhaust() {
        val actor = fixture()
        val inbox = publish(actor)
        val lease = claim()
        database()
            .execute(
                """CREATE FUNCTION block_email_completion() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.resource_id='$inbox'::uuid AND NEW.action='communications.email_accepted' THEN RAISE EXCEPTION 'fixture'; END IF; RETURN NEW; END $$"""
            )
        database()
            .execute(
                "CREATE TRIGGER block_email_completion BEFORE INSERT ON audit_entries FOR EACH ROW EXECUTE FUNCTION block_email_completion()"
            )
        try {
            assertTrue(deliver.execute(lease) is Result.Failed)
            assertEquals("LEASED", row(inbox)["state"])
            assertEquals(
                "LEASED",
                database()
                    .queryForObject(
                        "select state from announcement_email_delivery where inbox_id=?",
                        String::class.java,
                        inbox,
                    ),
            )
        } finally {
            database().execute("DROP TRIGGER block_email_completion ON audit_entries")
            database().execute("DROP FUNCTION block_email_completion()")
        }
        expireLease(inbox)
        repeat(7) {
            claim()
            expireLease(inbox)
        }
        assertEquals(
            Result.Success(emptyList<InboxEmailLease>()),
            leaseEmail.execute(UUID.randomUUID(), 1),
        )
        assertEquals("FAILED", row(inbox)["state"])
        assertEquals("email_attempts_exhausted", row(inbox)["failure_code"])
        assertEquals(8, row(inbox)["attempts"])
        assertEquals(1, probe.messages.size)
    }

    @Test
    fun disabledDeliveryStillExpiresAndPurgesOperationalRowsWithoutDeletingInbox() {
        val actor = fixture()
        val inbox = publish(actor)
        val future = Instant.now().plusSeconds(2 * 86400)
        val cleanup =
            LeaseInboxEmail(
                dispatches,
                transactions,
                journal,
                InboxEmailPolicy(false),
                Clock.fixed(future, ZoneOffset.UTC),
            )
        assertEquals(
            Result.Success(emptyList<InboxEmailLease>()),
            cleanup.execute(UUID.randomUUID(), 1),
        )
        assertEquals("email_delivery_expired", row(inbox)["failure_code"])
        assertEquals(
            "FAILED",
            database()
                .queryForObject(
                    "select state from announcement_email_delivery where inbox_id=?",
                    String::class.java,
                    inbox,
                ),
        )
        val retention =
            LeaseInboxEmail(
                dispatches,
                transactions,
                journal,
                InboxEmailPolicy(false),
                Clock.fixed(future.plusSeconds(8 * 86400), ZoneOffset.UTC),
            )
        assertEquals(
            Result.Success(emptyList<InboxEmailLease>()),
            retention.execute(UUID.randomUUID(), 1),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from inbox_email_dispatches where inbox_id=?",
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
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from announcement_email_delivery where inbox_id=?",
                    Int::class.java,
                    inbox,
                ),
        )
        assertTrue(probe.messages.isEmpty())
    }

    @Test
    fun workerShutdownInterruptsDeliveryAndReleasesItsOwnedExecutors() = verifyStoppedDelivery(true)

    @Test
    fun deadlineInterruptsStalledDeliveryWithoutAcknowledgingIt() = verifyStoppedDelivery(false)

    private fun verifyStoppedDelivery(byShutdown: Boolean) {
        val actor = fixture()
        val inbox = publish(actor)
        val entered = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val release = CountDownLatch(1)
        probe.send.set {
            entered.countDown()
            try {
                check(release.await(5, TimeUnit.SECONDS))
                Result.Success(Unit)
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
                setThreadNamePrefix("email-fixture-")
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
            InboxEmailWorker(
                leaseEmail,
                deliver,
                tasks,
                timers,
                InboxEmailWorkerSettings(1, Duration.ofMillis(25), Duration.ofSeconds(2)),
            )
        try {
            worker.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            if (byShutdown) worker.stop()
            assertTrue(interrupted.await(3, TimeUnit.SECONDS))
            worker.stop()
            assertFalse(worker.isRunning)
            assertTrue(tasks.threadPoolExecutor.isTerminated)
            assertTrue(timers.scheduledThreadPoolExecutor.isTerminated)
            assertEquals("LEASED", row(inbox)["state"])
        } finally {
            release.countDown()
            worker.stop()
        }
        probe.send.set { Result.Success(Unit) }
        expireLease(inbox)
        finish(inbox)
        assertEquals("ACCEPTED", row(inbox)["state"])
    }

    @Test
    fun actualSmtpAcceptanceAndRejectionUpdateDurableEmailObservations() {
        val actor = fixture()
        for (reply in listOf(250, 450, 550)) {
            val inbox = publish(actor)
            SmtpFixture(finalCode = reply).use { smtp ->
                val outbound =
                    dev.fajar.hris.mail.data.repositories.SmtpMailRepository(
                        dev.fajar.hris.mail.data.datasources.JakartaMailDataSource(
                            dev.fajar.hris.mail.data.di.createSmtpSender(
                                dev.fajar.hris.mail.data.di.SmtpSettings(
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
                probe.send.set { outbound.send(it) }
                assertEquals(Result.Success(Unit), deliver.execute(claim()))
                val mime =
                    jakarta.mail.internet.MimeMessage(
                        jakarta.mail.Session.getInstance(java.util.Properties()),
                        java.io.ByteArrayInputStream(
                            smtp.message.get(3, TimeUnit.SECONDS).toByteArray(Charsets.US_ASCII)
                        ),
                    )
                assertEquals("<$inbox@example.test>", mime.messageID)
                assertEquals("New HRIS announcement", mime.subject)
                assertEquals(
                    listOf("${row(inbox)["account_id"]}@example.test"),
                    mime.allRecipients.map { it.toString() },
                )
                assertTrue(mime.content.toString().contains("/inbox/$inbox"))
                assertFalse(mime.content.toString().contains("Office update"))
                smtp.awaitConnectionClosed()
            }
            val expected =
                when (reply) {
                    250 -> "ACCEPTED"
                    450 -> "PENDING"
                    else -> "FAILED"
                }
            assertEquals(expected, row(inbox)["state"])
            assertEquals(
                expected,
                database()
                    .queryForObject(
                        "select state from announcement_email_delivery where inbox_id=?",
                        String::class.java,
                        inbox,
                    ),
            )
        }
    }

    @Test
    fun competingWorkersRespectTheDeploymentCapacityAndUniqueLeases() {
        val actor = fixture()
        repeat(18) { publish(actor) }
        val captured = java.util.concurrent.ConcurrentLinkedQueue<InboxEmailLease>()
        Executors.newFixedThreadPool(4).use { pool ->
            repeat(4) {
                val futures =
                    (1..4).map {
                        pool.submit {
                            val result = leaseEmail.execute(UUID.randomUUID(), 4)
                            assertTrue(result is Result.Success, result.toString())
                            captured.addAll((result as Result.Success).value)
                        }
                    }
                futures.forEach { it.get(10, TimeUnit.SECONDS) }
            }
        }
        assertEquals(16, captured.size)
        assertEquals(16, captured.map { it.dispatch.inboxId }.toSet().size)
        assertEquals(16, captured.map { it.token }.toSet().size)
        assertEquals(
            Result.Success(emptyList<InboxEmailLease>()),
            leaseEmail.execute(UUID.randomUUID(), 1),
        )
        assertEquals(Result.Success(Unit), deliver.execute(captured.first()))
        assertEquals(1, (leaseEmail.execute(UUID.randomUUID(), 4) as Result.Success).value.size)
    }
}
