package dev.fajar.hris

import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(LifecycleReadProbeConfiguration::class)
class LifecycleSnapshotHttpTest : LifecycleApiFixture() {
    @Autowired private lateinit var probe: LifecycleReadProbe

    @Test
    fun paginatedCasesRetainOneSnapshotWhileAnotherRequestChangesATask() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val template = template(browser, csrf, company, optional = true)
        val ids =
            List(2) { case(browser, csrf, company, employee(browser, csrf, company), template) }
                .sortedBy { it.toString() }
        val barrier = LifecycleReadProbe.Barrier(company)
        probe.listRead.set(barrier)
        val page =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<HttpResponse<String>> {
                        get(browser, "${api(company)}/cases?limit=1")
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    val changed = change(browser, csrf, company, ids[0], 0)
                    assertEquals(200, changed.statusCode(), changed.body())
                    barrier.release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    barrier.release.countDown()
                    probe.listRead.set(null)
                }
            }
        assertEquals(200, page.statusCode(), page.body())
        val body = json.readTree(page.body())
        assertEquals(1, body["items"].size())
        assertEquals(ids[0].toString(), body["nextCursor"].asString())
        assertEquals(0, body["items"][0]["version"].asLong())
        assertEquals(2, body["items"][0]["tasks"].size())
        assertTrue(
            body["items"][0]["tasks"].iterator().asSequence().all {
                it["status"].asString() == "PENDING"
            }
        )
        val current = json.readTree(get(browser, "${api(company)}/cases/${ids[0]}").body())
        assertEquals(1, current["version"].asLong())
        assertEquals("DONE", current["tasks"][0]["status"].asString())
        val next =
            json.readTree(get(browser, "${api(company)}/cases?limit=1&after=${ids[0]}").body())
        assertEquals(ids[1].toString(), next["items"][0]["id"].asString())
        assertTrue(next["nextCursor"].isNull)
        val otherCompany = company(browser, csrf)
        assertEquals(
            0,
            json.readTree(get(browser, "${api(otherCompany)}/cases").body())["items"].size(),
        )
    }

    @Test
    fun offboardingRechecksRecentAuthenticationAfterWaitingForTheCase() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val employee = employee(browser, csrf, company)
        val template = template(browser, csrf, company, "OFFBOARDING")
        val id = case(browser, csrf, company, employee, template, "2026-09-30")
        assertEquals(200, change(browser, csrf, company, id, 0).statusCode())
        val barrier = LifecycleReadProbe.Barrier(company)
        probe.caseWait.set(barrier)
        val response =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<HttpResponse<String>> {
                        finish(browser, csrf, company, id, 1, offboarding = true)
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plus(Duration.ofMinutes(31)))
                    barrier.release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    barrier.release.countDown()
                    probe.caseWait.set(null)
                }
            }
        assertEquals(403, response.statusCode(), response.body())
        assertEquals(
            "recent_authentication_required",
            json.readTree(response.body())["code"].asString(),
        )
        assertEquals(
            "OPEN",
            database()
                .queryForObject(
                    "select status from lifecycle_cases where company_id=? and id=?",
                    String::class.java,
                    company,
                    id,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select version from employments where company_id=? and id=?",
                    Int::class.java,
                    company,
                    employee,
                ),
        )
    }
}
