package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.workforce.domain.entities.WorkHoliday
import dev.fajar.hris.workforce.domain.usecases.GetWorkHoliday
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class HolidayDetailsHttpTest : WorkforceAccessApiFixture() {
    @Autowired private lateinit var details: GetWorkHoliday

    @Test
    fun detailsRetainInactiveDatesAndVersionsWithoutCrossCompanyDisclosure() {
        val f = isolatedFixture()
        val other = isolatedFixture()
        val id = createHoliday(f)
        val foreign = createHoliday(other)
        val path = "${f.path}/holidays/$id"
        val initial = get(f.admin, path)
        assertEquals(200, initial.statusCode(), initial.body())
        val holiday = json.readTree(initial.body())
        assertEquals(id.toString(), holiday["id"].asString())
        assertEquals("2026-10-02", holiday["workDate"].asString())
        assertEquals("Company holiday", holiday["name"].asString())
        assertEquals(0, holiday["version"].asLong())
        assertTrue(holiday["active"].asBoolean())
        assertEquals(403, get(f.employeeClient, path).statusCode())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'company.read')",
                f.company,
                f.employeeAccount,
            )
        assertEquals(200, get(f.employeeClient, path).statusCode())
        for (missing in listOf(foreign, UUID.randomUUID())) {
            val response = get(f.admin, "${f.path}/holidays/$missing")
            assertEquals(404, response.statusCode(), response.body())
            assertEquals("holiday_not_found", json.readTree(response.body())["code"].asString())
        }
        val updated =
            command(
                f.admin,
                path,
                json.writeValueAsString(
                    mapOf(
                        "workDate" to "2026-10-03",
                        "name" to "Rescheduled holiday",
                        "active" to false,
                        "expectedVersion" to 0,
                        "reason" to "Deactivate holiday",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, updated.statusCode(), updated.body())
        val current = get(f.admin, path)
        assertEquals(200, current.statusCode(), current.body())
        val revised = json.readTree(current.body())
        assertEquals(1, revised["version"].asLong())
        assertEquals("2026-10-03", revised["workDate"].asString())
        assertFalse(revised["active"].asBoolean())
    }

    @Test
    fun authorityChangesDuringTheDetailGuardPreventDisclosure() {
        for (mode in listOf("permissions", "credentials", "membership")) {
            val f = isolatedFixture()
            val id = createHoliday(f)
            val barrier = AccountLockProbe.Barrier(f.actor.accountId, occurrence = 2)
            accountProbe.current.set(barrier)
            val result =
                Executors.newSingleThreadExecutor().use { pool ->
                    val pending =
                        pool.submit<HttpResponse<String>> { get(f.admin, "${f.path}/holidays/$id") }
                    try {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        when (mode) {
                            "credentials" ->
                                database()
                                    .update(
                                        "update accounts set security_version=security_version+1 where id=?",
                                        f.actor.accountId,
                                    )
                            "membership" ->
                                database()
                                    .update(
                                        "update company_memberships set active=false where company_id=? and account_id=?",
                                        f.company,
                                        f.actor.accountId,
                                    )
                            else ->
                                database()
                                    .update(
                                        "delete from membership_permissions where company_id=? and account_id=? and permission='company.read'",
                                        f.company,
                                        f.actor.accountId,
                                    )
                        }
                        barrier.release.countDown()
                        pending.get(10, TimeUnit.SECONDS)
                    } finally {
                        barrier.release.countDown()
                        accountProbe.current.set(null)
                    }
                }
            assertEquals(
                if (mode == "credentials") 401 else 403,
                result.statusCode(),
                result.body(),
            )
            assertFalse(result.body().contains("Company holiday"))
        }
    }

    @Test
    fun cancellationReleasesSharedCalendarAndAccessGuardsWithoutBlockingAnotherReader() {
        val f = isolatedFixture()
        val id = createHoliday(f)
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'company.read')",
                f.company,
                f.employeeAccount,
            )
        val barrier = AccountLockProbe.Barrier(f.actor.accountId)
        val finished = CountDownLatch(1)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<WorkHoliday>> {
                    try {
                        details.execute(f.actor, id)
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val second = get(f.employeeClient, "${f.path}/holidays/$id")
                assertEquals(200, second.statusCode(), second.body())
                assertEquals(
                    false,
                    database()
                        .queryForObject(
                            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                            Boolean::class.java,
                            "calendar:${f.company}",
                        ),
                )
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertTrue(pending.isCancelled)
                assertEquals(
                    true,
                    database()
                        .queryForObject(
                            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                            Boolean::class.java,
                            "calendar:${f.company}",
                        ),
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(200, get(f.admin, "${f.path}/holidays/$id").statusCode())
        createShift(f)
    }
}
