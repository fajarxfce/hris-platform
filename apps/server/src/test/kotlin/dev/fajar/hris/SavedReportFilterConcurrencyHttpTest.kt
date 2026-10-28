package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SavedReportFilterConcurrencyHttpTest : SavedReportFilterApiFixture() {
    @Test
    fun competingCreatesShareTheOwnerLimitAndDeletionReleasesOnlyAnActiveSlot() {
        val f = fixture()
        val initial = List(49) { UUID.randomUUID() }
        initial.forEachIndexed { index, id -> ok(save(f, id, input = definition("Filter $index"))) }
        val candidates = List(2) { UUID.randomUUID() }
        val start = CountDownLatch(1)
        val outcomes =
            Executors.newFixedThreadPool(2).use { pool ->
                val pending =
                    candidates.map { id ->
                        pool.submit<HttpResponse<String>> {
                            check(start.await(5, TimeUnit.SECONDS))
                            save(f, id)
                        }
                    }
                start.countDown()
                pending.map { it.get(15, TimeUnit.SECONDS) }
            }
        assertEquals(listOf(200, 409), outcomes.map { it.statusCode() }.sorted())
        val denied = outcomes.single { it.statusCode() == 409 }
        assertEquals("report_filter_limit", json.readTree(denied.body())["code"].asString())
        assertEquals(50, listed(f).size())
        ok(delete(f, initial.first(), 0))
        val rejectedId = candidates[outcomes.indexOf(denied)]
        ok(save(f, rejectedId))
        assertEquals(50, listed(f).size())
        failure(save(f, initial.first()), 404, "report_filter_not_found")
        assertEquals(
            51,
            database()
                .queryForObject(
                    "select count(*) from saved_report_filters where company_id=? and account_id=?",
                    Int::class.java,
                    f.company,
                    f.account,
                ),
        )
    }

    @Test
    fun duplicateDeliveriesReturnOneReceiptAndCompetingObservedVersionsCannotOverwriteEachOther() {
        val f = fixture()
        val id = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending =
                List(2) {
                    pool.submit<HttpResponse<String>> {
                        check(start.await(5, TimeUnit.SECONDS))
                        save(f, id, operation = operation)
                    }
                }
            start.countDown()
            val replies = pending.map { ok(it.get(15, TimeUnit.SECONDS)) }
            assertEquals(replies[0], replies[1])
        }
        assertEquals(listOf(1, 1, 1), evidence(f, id))
        val update = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending =
                List(2) { index ->
                    pool.submit<HttpResponse<String>> {
                        check(update.await(5, TimeUnit.SECONDS))
                        save(f, id, 0, input = definition("Edit $index"))
                    }
                }
            update.countDown()
            val replies = pending.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 409), replies.map { it.statusCode() }.sorted())
            assertEquals(
                "stale_version",
                json.readTree(replies.single { it.statusCode() == 409 }.body())["code"].asString(),
            )
        }
        assertEquals(1, ok(get(f.browser, "${base(f)}/$id"))["version"].asLong())
        assertEquals(listOf(2, 2, 2), evidence(f, id))
    }

    @Test
    fun failureAfterWritingOrJournalingRollsBackTheFilterReceiptAuditAndOutboxTogether() {
        for (stage in listOf("save", "journal")) {
            val f = fixture()
            val id = UUID.randomUUID()
            val operation = UUID.randomUUID()
            if (stage == "save")
                filterProbe.failAfterSave.set(IllegalStateException("PRIVATE FILTER VALUE"))
            else filterProbe.rejectJournal.set(true)
            val response = save(f, id, operation = operation)
            failure(
                response,
                if (stage == "save") 500 else 503,
                if (stage == "save") "database_failure" else "database_busy",
            )
            assertFalse(response.body().contains("PRIVATE FILTER VALUE"))
            assertEquals(0, listed(f).size())
            assertEquals(listOf(0, 0, 0), evidence(f, id))
            ok(save(f, id, operation = operation))
            val deletion = UUID.randomUUID()
            filterProbe.rejectJournal.set(true)
            failure(delete(f, id, 0, deletion), 503, "database_busy")
            assertEquals(0, ok(get(f.browser, "${base(f)}/$id"))["version"].asLong())
            assertEquals(listOf(1, 1, 1), evidence(f, id))
            ok(delete(f, id, 0, deletion))
            assertEquals(listOf(2, 2, 2), evidence(f, id))
        }
    }

    @Test
    fun cancellationAfterTheWriteReleasesOwnershipAndAllowsTheOriginalOperationToBeRetried() {
        val f = fixture()
        val id = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val gate = SavedReportFilterProbe.Gate(f.account, "save")
        val finished = CountDownLatch(1)
        filterProbe.gate.set(gate)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit {
                    try {
                        saveFilter.execute(f.actor, id, operation, definition(), null)
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
            } finally {
                gate.release.countDown()
                filterProbe.gate.set(null)
            }
        }
        assertEquals(0, listed(f).size())
        assertEquals(listOf(0, 0, 0), evidence(f, id))
        ok(save(f, id, operation = operation))
        assertEquals(listOf(1, 1, 1), evidence(f, id))
    }
}
