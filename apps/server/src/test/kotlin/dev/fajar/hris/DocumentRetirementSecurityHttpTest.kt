package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.usecases.RetireDocumentRevision
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class DocumentRetirementSecurityHttpTest : DocumentRetirementApiFixture() {
    @Autowired private lateinit var accountProbe: AccountLockProbe
    @Autowired private lateinit var retireRevision: RetireDocumentRevision

    @Test
    fun competingRetirementsProduceOneEvidenceAndLostResponsesReplay() {
        for (sameKey in listOf(false, true)) {
            val initial = retentionFixture()
            assertEquals(200, retentionPolicy(initial, days = 1).statusCode())
            val doc = UUID.randomUUID()
            val rev = readyRevision(initial, doc)
            assertEquals(200, retentionChange(initial, doc, "archive").statusCode())
            val f = elapsedRetention(initial)
            val version = revision(f, rev).get("version").asLong()
            val operation = UUID.randomUUID()
            val gate = CountDownLatch(1)
            Executors.newFixedThreadPool(2).use { pool ->
                val pending =
                    (1..2).map {
                        pool.submit<java.net.http.HttpResponse<String>> {
                            check(gate.await(5, TimeUnit.SECONDS))
                            retire(
                                f,
                                rev,
                                version,
                                key = if (sameKey) operation else UUID.randomUUID(),
                            )
                        }
                    }
                gate.countDown()
                val responses = pending.map { it.get(15, TimeUnit.SECONDS) }
                assertEquals(
                    if (sameKey) listOf(200, 200) else listOf(200, 409),
                    responses.map { it.statusCode() }.sorted(),
                    responses.map { it.body() }.toString(),
                )
                if (sameKey) assertEquals(responses[0].body(), responses[1].body())
            }
            assertEquals(1, garbage(rev))
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from document_retirements where company_id=?",
                        Int::class.java,
                        f.company,
                    ),
            )
        }
    }

    @Test
    fun holdAndRestoreCompeteWithRetirementThroughTheSameRetentionVersion() {
        for (action in listOf("hold", "restore")) {
            val initial = retentionFixture()
            assertEquals(200, retentionPolicy(initial, days = 1).statusCode())
            val doc = UUID.randomUUID()
            val rev = readyRevision(initial, doc)
            assertEquals(200, retentionChange(initial, doc, "archive").statusCode())
            val f = elapsedRetention(initial)
            val version = revision(f, rev).get("version").asLong()
            val gate = CountDownLatch(1)
            Executors.newFixedThreadPool(2).use { pool ->
                val retired =
                    pool.submit<java.net.http.HttpResponse<String>> {
                        check(gate.await(5, TimeUnit.SECONDS))
                        retire(f, rev, version)
                    }
                val changed =
                    pool.submit<java.net.http.HttpResponse<String>> {
                        check(gate.await(5, TimeUnit.SECONDS))
                        retentionChange(f, doc, action, 0)
                    }
                gate.countDown()
                val responses =
                    listOf(retired.get(15, TimeUnit.SECONDS), changed.get(15, TimeUnit.SECONDS))
                assertEquals(
                    listOf(200, 409),
                    responses.map { it.statusCode() }.sorted(),
                    responses.map { it.body() }.toString(),
                )
            }
            if (revision(f, rev).get("status").asString() == "RETIRED") {
                assertEquals(1, garbage(rev))
                assertEquals(200, retentionChange(f, doc, "hold", 1).statusCode())
                assertEquals("RETIRED", revision(f, rev).get("status").asString())
                assertEquals(1, garbage(rev))
            } else assertEquals(0, garbage(rev))
        }
    }

    @Test
    fun permissionCredentialAndStepUpChangesDuringWaitPreventRetirement() {
        for (mode in listOf("permission", "credential", "recent")) {
            val initial = retentionFixture()
            assertEquals(200, retentionPolicy(initial, days = 1).statusCode())
            val doc = UUID.randomUUID()
            val rev = readyRevision(initial, doc)
            assertEquals(200, retentionChange(initial, doc, "archive").statusCode())
            val f = elapsedRetention(initial)
            val version = revision(f, rev).get("version").asLong()
            val operation = UUID.randomUUID()
            val barrier = AccountLockProbe.Barrier(f.actor.accountId)
            accountProbe.current.set(barrier)
            try {
                Executors.newSingleThreadExecutor().use { pool ->
                    val pending =
                        pool.submit<java.net.http.HttpResponse<String>> {
                            retire(f, rev, version, key = operation)
                        }
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    when (mode) {
                        "permission" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='documents.retention'",
                                    f.company,
                                    f.actor.accountId,
                                )
                        "credential" ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    f.actor.accountId,
                                )
                        else -> clock.set(clock.instant().plusSeconds(601))
                    }
                    barrier.release.countDown()
                    val response = pending.get(15, TimeUnit.SECONDS)
                    assertEquals(
                        if (mode == "credential") 401 else 403,
                        response.statusCode(),
                        response.body(),
                    )
                }
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
            assertEquals(0, garbage(rev))
            assertEquals(
                "READY",
                database()
                    .queryForObject(
                        "select status from document_revisions where id=?",
                        String::class.java,
                        rev,
                    ),
            )
            if (mode == "permission")
                database()
                    .update(
                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.retention')",
                        f.company,
                        f.actor.accountId,
                    )
            val refreshed =
                f.copy(
                    csrf = login(f.browser),
                    actor = f.actor.copy(authenticatedAt = clock.instant()),
                )
            val retry = retire(refreshed, rev, version, key = operation)
            assertEquals(200, retry.statusCode(), retry.body())
            database()
                .update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission='documents.retention'",
                    f.company,
                    f.actor.accountId,
                )
            assertEquals(403, retire(refreshed, rev, version, key = operation).statusCode())
            clock.set(Instant.now())
        }
    }

    @Test
    fun interruptedRetirementReleasesItsGuardWithoutConsumingTheOperation() {
        val initial = retentionFixture()
        assertEquals(200, retentionPolicy(initial, days = 1).statusCode())
        val doc = UUID.randomUUID()
        val rev = readyRevision(initial, doc)
        assertEquals(200, retentionChange(initial, doc, "archive").statusCode())
        val f = elapsedRetention(initial)
        val version = revision(f, rev).get("version").asLong()
        val operation = UUID.randomUUID()
        val barrier = AccountLockProbe.Barrier(f.actor.accountId)
        val exited = CountDownLatch(1)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<Result<MutationReceipt>> {
                        try {
                            retireRevision.execute(
                                f.actor,
                                operation,
                                rev,
                                version,
                                0,
                                "Retention expired",
                            )
                        } finally {
                            exited.countDown()
                        }
                    }
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                pending.cancel(true)
                assertTrue(exited.await(5, TimeUnit.SECONDS))
            }
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
        assertEquals(0, garbage(rev))
        assertEquals("READY", revision(f, rev).get("status").asString())
        assertEquals(200, retire(f, rev, version, key = operation).statusCode())
    }

    @Test
    fun malformedVersionsAndReasonsDoNotChangeEligibilityOrRevealForeignContent() {
        val f = retentionFixture()
        val doc = UUID.randomUUID()
        val rev = readyRevision(f, doc)
        assertEquals(422, retire(f, rev, -1).statusCode())
        assertEquals(422, retire(f, rev, retentionVersion = 10000).statusCode())
        assertEquals(422, retire(f, rev, reason = "").statusCode())
        val other = retentionFixture()
        assertEquals(404, retire(other, rev, 0).statusCode())
        assertEquals("READY", revision(f, rev).get("status").asString())
        assertEquals(0, garbage(rev))
    }
}
