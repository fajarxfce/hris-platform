package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeavePolicyReviewHttpTest : LeavePolicyApiFixture() {
    @Test
    fun administrativeHeadsIncludeFutureAndInactivePoliciesWithoutChangingEffectiveSelection() {
        val f = leaveFixture()
        reviewBody(policy(f, 0, from = "2027-01-01", paid = false))
        reviewBody(policy(f, 1, from = "2027-06-01", active = false))
        val medical = f.copy(type = UUID.randomUUID())
        reviewBody(policy(medical, from = "2028-01-01", code = "MEDICAL"))
        val page = reviewBody(get(f.admin, "${policyPath(f)}?limit=1"))
        assertEquals("ANNUAL", page["nextCursor"].asString())
        val first = page["items"][0]
        assertEquals(f.type.toString(), first["id"].asString())
        assertEquals(2, first["version"].asLong())
        assertEquals(2, first["appliedRevision"].asLong())
        assertFalse(first["active"].asBoolean())
        assertEquals("2027-06-01", first["effectiveFrom"].asString())
        val next = reviewBody(get(f.admin, "${policyPath(f)}?limit=1&after=ANNUAL"))
        assertEquals(medical.type.toString(), next["items"][0]["id"].asString())
        assertTrue(next["nextCursor"].isNull)
        assertEquals(
            "MEDICAL",
            reviewBody(get(f.admin, "${policyPath(f)}?active=true"))["items"][0]["code"].asString(),
        )
        assertEquals(
            "ANNUAL",
            reviewBody(get(f.admin, "${policyPath(f)}?active=false"))["items"][0]["code"].asString(),
        )
        val effective =
            reviewBody(get(f.worker, "/api/v1/companies/${f.company}/leave/types?asOf=2026-10-01"))[
                "items"]
        assertEquals(1, effective.size())
        assertEquals(0, effective[0]["appliedRevision"].asLong())
        assertEquals(2, effective[0]["version"].asLong())
        assertTrue(effective[0]["active"].asBoolean())
    }

    @Test
    fun reviewKeepsTheCurrentHeadWhilePagingImmutableRevisionEvidence() {
        val f = leaveFixture()
        reviewBody(policy(f, 0, from = "2026-11-01", paid = false))
        reviewBody(policy(f, 1, from = "2027-01-01", active = false))
        val first = reviewBody(get(f.admin, "${policyPath(f)}/${f.type}?historyLimit=2"))
        assertEquals(2, first["current"]["version"].asLong())
        assertEquals(
            listOf(2L, 1L),
            first["history"]["items"]
                .iterator()
                .asSequence()
                .map { it["revision"].asLong() }
                .toList(),
        )
        assertEquals("1", first["history"]["nextCursor"].asString())
        assertEquals(
            "Leave policy configuration",
            first["history"]["items"][0]["reason"].asString(),
        )
        assertNotNull(UUID.fromString(first["history"]["items"][0]["actorId"].asString()))
        assertFalse(first["history"]["items"][1]["paid"].asBoolean())
        val next =
            reviewBody(get(f.admin, "${policyPath(f)}/${f.type}?historyLimit=2&historyAfter=1"))
        assertEquals(first["current"], next["current"])
        assertEquals(1, next["history"]["items"].size())
        assertEquals(0, next["history"]["items"][0]["revision"].asLong())
        assertTrue(next["history"]["items"][0]["paid"].asBoolean())
        assertTrue(next["history"]["nextCursor"].isNull)
        for (suffix in
            listOf("historyAfter=-1", "historyAfter=3", "historyLimit=0", "historyLimit=201")) {
            val invalid = get(f.admin, "${policyPath(f)}/${f.type}?$suffix")
            assertEquals(422, invalid.statusCode(), invalid.body())
            assertEquals("invalid_page", json.readTree(invalid.body())["code"].asString())
        }
        for (suffix in
            listOf("limit=0", "limit=201", "after=%20", "after=" + "A".repeat(33))) assertEquals(
            422,
            get(f.admin, "${policyPath(f)}?$suffix").statusCode(),
        )
    }

    @Test
    fun policyAdministrationDoesNotGrantSelfOrTeamReadersHistoricalReasonsOrForeignResources() {
        val f = leaveFixture()
        for (client in listOf(f.worker, f.supervisor)) {
            assertEquals(403, get(client, policyPath(f)).statusCode())
            assertEquals(403, get(client, "${policyPath(f)}/${f.type}").statusCode())
            reviewBody(get(client, "/api/v1/companies/${f.company}/leave/types?asOf=2026-10-01"))
        }
        val other = leaveFixture()
        val missing = get(f.admin, "${policyPath(f)}/${other.type}")
        assertEquals(404, missing.statusCode())
        assertEquals("leave_type_not_found", json.readTree(missing.body())["code"].asString())
        assertFalse(missing.body().contains(other.company.toString()))
    }

    @Test
    fun concurrentReadersShareThePolicyGuardAndOneReviewCannotMixHeadAndHistoryAcrossASave() {
        val f = leaveFixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = AtomicBoolean(true)
        policyReadProbe.afterFind = { company, id ->
            if (company == f.company && id == f.type && first.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        Executors.newFixedThreadPool(3).use { pool ->
            val reading =
                pool.submit<HttpResponse<String>> { get(f.admin, "${policyPath(f)}/${f.type}") }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val second =
                    pool.submit<HttpResponse<String>> { get(f.admin, "${policyPath(f)}/${f.type}") }
                assertEquals(
                    0,
                    reviewBody(second.get(3, TimeUnit.SECONDS))["current"]["version"].asLong(),
                )
                val saving = pool.submit<HttpResponse<String>> { policy(f, 0, active = false) }
                assertThrows(TimeoutException::class.java) {
                    saving.get(150, TimeUnit.MILLISECONDS)
                }
                release.countDown()
                val old = reviewBody(reading.get(5, TimeUnit.SECONDS))
                assertEquals(0, old["current"]["version"].asLong())
                assertEquals(
                    listOf(0L),
                    old["history"]["items"]
                        .iterator()
                        .asSequence()
                        .map { it["revision"].asLong() }
                        .toList(),
                )
                reviewBody(saving.get(5, TimeUnit.SECONDS))
                val fresh = reviewBody(get(f.admin, "${policyPath(f)}/${f.type}"))
                assertEquals(1, fresh["current"]["version"].asLong())
                assertEquals(
                    listOf(1L, 0L),
                    fresh["history"]["items"]
                        .iterator()
                        .asSequence()
                        .map { it["revision"].asLong() }
                        .toList(),
                )
            } finally {
                release.countDown()
                policyReadProbe.afterFind = null
            }
        }
    }

    @Test
    fun cancellationDuringAReadReleasesItsTransactionAndPermitsAFollowingSave() {
        val f = leaveFixture()
        val actor = policyOperator(f)
        val invoke =
            policyInvocation(f, actor, "review", IdentitySecurityPolicy(enforceMfa = false))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        policyReadProbe.afterFind = { company, id ->
            if (company == f.company && id == f.type) {
                entered.countDown()
                try {
                    check(release.await(5, TimeUnit.SECONDS))
                } finally {
                    interrupted.countDown()
                }
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { invoke(actor) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(interrupted.await(3, TimeUnit.SECONDS))
                policyReadProbe.afterFind = null
                reviewBody(policy(f, 0))
                assertEquals(
                    1,
                    reviewBody(get(f.admin, "${policyPath(f)}/${f.type}"))["current"]["version"]
                        .asLong(),
                )
            } finally {
                release.countDown()
                policyReadProbe.afterFind = null
            }
        }
    }
}
