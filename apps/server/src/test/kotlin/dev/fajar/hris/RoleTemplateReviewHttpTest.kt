package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RoleTemplateReviewHttpTest : RoleTemplateApiFixture() {
    @Test
    fun currentDefinitionAndBoundedHistoryRetainInactiveAndOriginalRevisions() {
        val f = roleFixture()
        body(save(f, 0))
        body(save(f, 1, active = false))
        val first = body(get(f.browser, "${f.path}/${f.id}?historyLimit=2"))
        assertEquals(f.id.toString(), first["current"]["id"].asString())
        assertEquals(2, first["current"]["version"].asLong())
        assertFalse(first["current"]["active"].asBoolean())
        assertEquals(
            listOf(2L, 1L),
            first["history"]["items"]
                .iterator()
                .asSequence()
                .map { it["revision"].asLong() }
                .toList(),
        )
        assertEquals("1", first["history"]["nextCursor"].asString())
        val latest = first["history"]["items"][0]
        assertEquals("Reviewed role definition", latest["reason"].asString())
        assertNotNull(UUID.fromString(latest["actorId"].asString()))
        assertNotNull(java.time.Instant.parse(latest["recordedAt"].asString()))
        assertFalse(latest["active"].asBoolean())
        val next = body(get(f.browser, "${f.path}/${f.id}?historyLimit=2&historyAfter=1"))
        assertEquals(first["current"], next["current"])
        assertEquals(
            listOf(0L),
            next["history"]["items"]
                .iterator()
                .asSequence()
                .map { it["revision"].asLong() }
                .toList(),
        )
        assertTrue(next["history"]["items"][0]["active"].asBoolean())
        assertTrue(next["history"]["nextCursor"].isNull)
        assertEquals(
            0,
            body(get(f.browser, "${f.path}/${f.id}?historyAfter=0"))["history"]["items"].size(),
        )
        for (query in
            listOf("historyAfter=-1", "historyAfter=3", "historyLimit=0", "historyLimit=201")) {
            val invalid = get(f.browser, "${f.path}/${f.id}?$query")
            assertEquals(422, invalid.statusCode(), invalid.body())
            assertEquals("invalid_page", json.readTree(invalid.body())["code"].asString())
        }
    }

    @Test
    fun roleHistoryRequiresCurrentCompanyAdministrationAndDoesNotRevealForeignDefinitions() {
        val f = roleFixture()
        val other = roleFixture()
        val missing = get(f.browser, "${f.path}/${other.id}")
        assertEquals(404, missing.statusCode())
        assertEquals("role_template_not_found", json.readTree(missing.body())["code"].asString())
        assertFalse(missing.body().contains(other.company.toString()))
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='identity.manage'",
                f.company,
                f.operator,
            )
        val reader = client()
        login(reader, "${f.operator}@example.test")
        assertEquals(403, get(reader, "${f.path}/${f.id}").statusCode())
        assertEquals(403, get(reader, f.path).statusCode())
        assertEquals(403, get(reader, "${f.path}/permissions").statusCode())
    }

    @Test
    fun reviewReadersShareTheirGuardAndCannotMixCurrentAndHistoricalRevisionsAcrossASave() {
        val f = roleFixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writerEntered = CountDownLatch(1)
        val first = AtomicBoolean(true)
        roleProbe.afterFind = { company, id ->
            if (company == f.company && id == f.id && first.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        roleProbe.beforeLock = { company, shared ->
            if (company == f.company && !shared) writerEntered.countDown()
        }
        Executors.newFixedThreadPool(3).use { pool ->
            val reading = pool.submit<HttpResponse<String>> { get(f.browser, "${f.path}/${f.id}") }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val second =
                    pool.submit<HttpResponse<String>> { get(f.browser, "${f.path}/${f.id}") }
                assertEquals(
                    0,
                    body(second.get(3, TimeUnit.SECONDS))["current"]["version"].asLong(),
                )
                val saving = pool.submit<HttpResponse<String>> { save(f, 0, active = false) }
                assertTrue(writerEntered.await(3, TimeUnit.SECONDS))
                assertThrows(TimeoutException::class.java) {
                    saving.get(100, TimeUnit.MILLISECONDS)
                }
                release.countDown()
                val old = body(reading.get(5, TimeUnit.SECONDS))
                assertEquals(0, old["current"]["version"].asLong())
                assertEquals(
                    listOf(0L),
                    old["history"]["items"]
                        .iterator()
                        .asSequence()
                        .map { it["revision"].asLong() }
                        .toList(),
                )
                body(saving.get(5, TimeUnit.SECONDS))
                val current = body(get(f.browser, "${f.path}/${f.id}"))
                assertEquals(1, current["current"]["version"].asLong())
                assertEquals(
                    listOf(1L, 0L),
                    current["history"]["items"]
                        .iterator()
                        .asSequence()
                        .map { it["revision"].asLong() }
                        .toList(),
                )
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun cancelledReviewReleasesTheGuardForTheNextCommand() {
        val f = roleFixture()
        val actor = actor(f)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        roleProbe.afterFind = { company, id ->
            if (company == f.company && id == f.id) {
                entered.countDown()
                try {
                    check(release.await(5, TimeUnit.SECONDS))
                } finally {
                    interrupted.countDown()
                }
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { review(f, actor) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(interrupted.await(3, TimeUnit.SECONDS))
                roleProbe.afterFind = null
                body(save(f, 0))
                assertEquals(
                    1,
                    body(get(f.browser, "${f.path}/${f.id}"))["current"]["version"].asLong(),
                )
            } finally {
                release.countDown()
            }
        }
    }
}
