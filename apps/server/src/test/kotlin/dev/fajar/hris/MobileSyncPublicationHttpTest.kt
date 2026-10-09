package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.sync.data.datasources.SyncDataSource
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MobileSyncPublicationHttpTest : MobileSyncApiFixture() {
    @Test
    fun publicationHeadAndEventsBecomeVisibleTogetherAndCompetingPublishersDoNotSkipWork() {
        val f = expenseFixture()
        val initial = token(f)
        body(saveExpense(f))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val paused =
            publisher.decorated { delegate ->
                object : SyncDataSource by delegate {
                    override fun publish(limit: Int): Int =
                        delegate.publish(limit).also {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                }
            }
        Executors.newSingleThreadExecutor().use { executor ->
            val publishing = executor.submit<Result<Int>> { paused.publish(1) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select head_position from mobile_sync_heads where company_id=?",
                            Long::class.java,
                            f.company,
                        ),
                )
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from mobile_sync_changes where company_id=? and sequence is not null",
                            Int::class.java,
                            f.company,
                        ),
                )
                val second = f.copy(claim = UUID.randomUUID(), line = UUID.randomUUID())
                body(saveExpense(second))
                assertEquals(0, success(publisher.publish()))
                release.countDown()
                assertEquals(1, success(publishing.get(10, TimeUnit.SECONDS)))
                val first = body(changes(f, initial))
                assertEquals(listOf(f.claim.toString()), ids(first))
                assertTrue(first.get("pendingPublication").asBoolean())
                assertEquals(1, success(publisher.publish()))
                val next = body(changes(f, first.get("cursor").asString()))
                assertEquals(listOf(second.claim.toString()), ids(next))
                assertFalse(next.get("pendingPublication").asBoolean())
                assertEquals(
                    listOf(1L, 2L),
                    database()
                        .queryForList(
                            "select sequence from mobile_sync_changes where company_id=? order by sequence",
                            Long::class.java,
                            f.company,
                        ),
                )
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun businessTransactionsThatCommitLateEnterTheFeedAfterEarlierPublications() {
        val f = expenseFixture()
        val other = expenseFixture()
        val initial = token(f)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        syncProbe.beforeJournal = { row ->
            if (row.resourceId == f.claim) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        Executors.newSingleThreadExecutor().use { executor ->
            val late = executor.submit<java.net.http.HttpResponse<String>> { saveExpense(f) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertEquals(0, queueCount(f))
                body(saveExpense(other))
                assertEquals(1, success(publisher.publish()))
                assertEquals(0, queueCount(f))
                release.countDown()
                body(late.get(10, TimeUnit.SECONDS))
                assertEquals(1, success(publisher.publish()))
                assertEquals(listOf(f.claim.toString()), ids(body(changes(f, initial))))
            } finally {
                release.countDown()
                syncProbe.clear()
            }
        }
    }

    @Test
    fun aLatePublicationFailureRollsBackTheHeadAndCanBeRetriedExactlyOnce() {
        val f = expenseFixture()
        val initial = token(f)
        body(saveExpense(f))
        val failing =
            publisher.decorated { delegate ->
                object : SyncDataSource by delegate {
                    override fun publish(limit: Int): Int {
                        delegate.publish(limit)
                        throw IllegalStateException("private-publication-fixture")
                    }
                }
            }
        val result = failing.publish()
        assertTrue(result is Result.Failed)
        assertFalse(result.toString().contains("private-publication-fixture"))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select head_position from mobile_sync_heads where company_id=?",
                    Long::class.java,
                    f.company,
                ),
        )
        val pending = body(changes(f, initial))
        assertTrue(items(pending).isEmpty())
        assertTrue(pending.get("pendingPublication").asBoolean())
        assertEquals(1, success(publisher.publish()))
        assertEquals(0, success(publisher.publish()))
        assertEquals(listOf(f.claim.toString()), ids(body(changes(f, initial))))
    }
}
