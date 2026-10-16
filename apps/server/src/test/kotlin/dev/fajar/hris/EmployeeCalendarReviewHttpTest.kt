package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.workforce.domain.usecases.GetEmployeeCalendarReview
import dev.fajar.hris.workforce.domain.usecases.ListWorkforceEmployees
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class EmployeeCalendarReviewHttpTest : WorkforceAccessApiFixture() {
    @Autowired private lateinit var review: GetEmployeeCalendarReview
    @Autowired private lateinit var directory: ListWorkforceEmployees

    @Test
    fun directoryIsBoundedAndDoesNotRequireOrDisclosePersonalProfileAccess() {
        val f = isolatedFixture()
        val second = anotherEmployee(f)
        val foreign = isolatedFixture()
        val path = "${f.path}/employees?asOf=2026-10-01"
        assertEquals(403, get(f.employeeClient, path).statusCode())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'workforce.read')",
                f.company,
                f.employeeAccount,
            )
        val first = get(f.employeeClient, "$path&limit=1")
        assertEquals(200, first.statusCode(), first.body())
        val page = json.readTree(first.body())
        assertTrue(page["bulkRosterSupported"].asBoolean())
        assertEquals(1, page["items"].size())
        assertEquals(
            setOf("id", "employeeNumber", "legalName", "status", "version"),
            page["items"][0].properties().map { it.key }.toSet(),
        )
        val next = get(f.employeeClient, "$path&limit=1&after=${page["nextCursor"].asString()}")
        assertEquals(200, next.statusCode(), next.body())
        val nextPage = json.readTree(next.body())
        assertTrue(nextPage["bulkRosterSupported"].asBoolean())
        assertTrue(nextPage["nextCursor"].isNull)
        assertEquals(
            setOf(f.employee.toString(), second.toString()),
            setOf(page["items"][0]["id"].asString(), nextPage["items"][0]["id"].asString()),
        )
        val filtered = json.readTree(get(f.employeeClient, "$path&query=Another").body())
        assertEquals(second.toString(), filtered["items"].single()["id"].asString())
        assertFalse(first.body().contains(foreign.employee.toString()))
        for (query in
            listOf(
                "limit=0",
                "limit=51",
                "query=${"x".repeat(121)}",
                "after=",
                "after=${"E".repeat(33)}",
            )) {
            val rejected = get(f.employeeClient, "$path&$query")
            assertEquals(422, rejected.statusCode(), query)
            assertEquals("invalid_page", json.readTree(rejected.body())["code"].asString())
        }
        assertEquals(
            403,
            get(f.employeeClient, "${foreign.path}/employees?asOf=2026-10-01").statusCode(),
        )
        assertEquals(
            403,
            get(f.employeeClient, "/api/v1/companies/${f.company}/employees?asOf=2026-10-01")
                .statusCode(),
        )
    }

    @Test
    fun reviewKeepsEffectiveSnapshotsAndGlobalVersionsWithExplicitRosterOrigins() {
        val f = isolatedFixture()
        val shift = createShift(f)
        val root = "${f.path}/employees/${f.employee}"
        fun assign(from: String, days: Map<String, Any>, version: Long?) =
            command(
                f.admin,
                "$root/schedule",
                json.writeValueAsString(
                    mapOf(
                        "effectiveFrom" to from,
                        "days" to days,
                        "expectedVersion" to version,
                        "reason" to "Calendar review setup",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        val assigned =
            assign("2026-10-01", mapOf("THURSDAY" to mapOf("id" to shift, "version" to 0)), null)
        assertEquals(200, assigned.statusCode(), assigned.body())
        assertEquals(200, assign("2026-11-01", emptyMap(), 0).statusCode())
        val template = json.readTree(shiftBody(shift)) as tools.jackson.databind.node.ObjectNode
        template.put("expectedVersion", 0)
        template.put("active", false)
        template.put("startsAt", "09:00")
        assertEquals(
            200,
            command(
                    f.admin,
                    "${f.path}/shifts/$shift",
                    template.toString(),
                    f.csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
                .statusCode(),
        )
        val holiday = createHoliday(f)
        assertEquals(
            200,
            command(
                    f.admin,
                    "$root/roster/2026-10-03",
                    """{"shift":null,"reason":"Roster day off"}""",
                    f.csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
                .statusCode(),
        )
        val response = get(f.admin, "$root/calendar/review?from=2026-10-01&until=2026-10-03")
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        assertEquals(f.employee.toString(), body["employee"]["id"].asString())
        assertEquals("Example Employee", body["employee"]["legalName"].asString())
        assertEquals("2026-10-01", body["from"].asString())
        assertEquals("2026-10-03", body["until"].asString())
        assertEquals(1, body["calendar"]["scheduleVersion"].asLong())
        assertEquals(0, body["assignmentAtStart"]["revision"].asLong())
        assertEquals("2026-10-01", body["assignmentAtStart"]["effectiveFrom"].asString())
        val snapshot = body["assignmentAtStart"]["days"]["THURSDAY"]
        assertEquals(0, snapshot["revision"].asLong())
        assertEquals("08:00:00", snapshot["details"]["startsAt"].asString())
        val days = body["calendar"]["days"]
        assertEquals("PATTERN", days[0]["origin"].asString())
        assertEquals(480, days[0]["plannedMinutes"].asInt())
        assertEquals("HOLIDAY", days[1]["origin"].asString())
        assertEquals(holiday.toString(), days[1]["holidayId"].asString())
        assertEquals("ROSTER", days[2]["origin"].asString())
        assertEquals("OFF", days[2]["kind"].asString())
        assertEquals(0, days[2]["originVersion"].asLong())
        val prior =
            json.readTree(
                get(f.admin, "$root/calendar/review?from=2026-09-30&until=2026-09-30").body()
            )
        assertTrue(prior["assignmentAtStart"].isNull)
        assertEquals(1, prior["calendar"]["scheduleVersion"].asLong())
        assertEquals("UNASSIGNED", prior["calendar"]["days"][0]["kind"].asString())
        val stale = assign("2026-12-01", emptyMap(), 0)
        assertEquals(409, stale.statusCode(), stale.body())
        assertEquals("stale_version", json.readTree(stale.body())["code"].asString())
    }

    @Test
    fun reviewEnforcesCompanyScopePermissionsAndFiniteDates() {
        val f = isolatedFixture()
        val foreign = isolatedFixture()
        val path = "${f.path}/employees/${f.employee}/calendar/review"
        val range = "from=2026-10-01&until=2026-10-07"
        assertEquals(403, get(f.employeeClient, "$path?$range").statusCode())
        assertEquals(
            200,
            get(f.employeeClient, "${f.path}/employees/${f.employee}/calendar?$range").statusCode(),
        )
        for (id in listOf(foreign.employee, UUID.randomUUID())) {
            val response = get(f.admin, "${f.path}/employees/$id/calendar/review?$range")
            assertEquals(404, response.statusCode(), response.body())
            assertEquals("employee_not_found", json.readTree(response.body())["code"].asString())
        }
        for (dates in
            listOf("from=2026-10-01&until=2026-12-02", "from=2026-10-02&until=2026-10-01")) {
            val response = get(f.admin, "$path?$dates")
            assertEquals(422, response.statusCode(), response.body())
            assertEquals(
                "invalid_calendar_range",
                json.readTree(response.body())["code"].asString(),
            )
        }
        val accepted = get(f.admin, "$path?from=2026-10-01&until=2026-12-01")
        assertEquals(200, accepted.statusCode(), accepted.body())
        assertEquals(62, json.readTree(accepted.body())["calendar"]["days"].size())
        val original = f.actor.copy(permissions = setOf("company.read"))
        assertEquals(
            "access_denied",
            (review.execute(
                    original,
                    f.employee,
                    LocalDate.parse("2026-10-01"),
                    LocalDate.parse("2026-10-01"),
                ) as Result.Failed)
                .failure
                .code,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["directory", "review"])
    fun pendingReadsCannotUseRevokedAuthority(kind: String) {
        for (mode in listOf("permission", "credential", "membership")) {
            val f = isolatedFixture()
            val path =
                if (kind == "directory") "${f.path}/employees?asOf=2026-10-01"
                else
                    "${f.path}/employees/${f.employee}/calendar/review?from=2026-10-01&until=2026-10-07"
            val barrier = AccountLockProbe.Barrier(f.actor.accountId, occurrence = 2)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<HttpResponse<String>> { get(f.admin, path) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    when (mode) {
                        "credential" ->
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
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='workforce.read'",
                                    f.company,
                                    f.actor.accountId,
                                )
                    }
                    barrier.release.countDown()
                    val result = pending.get(10, TimeUnit.SECONDS)
                    assertEquals(
                        if (mode == "credential") 401 else 403,
                        result.statusCode(),
                        result.body(),
                    )
                    assertFalse(result.body().contains("Example Employee"))
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["directory", "review"])
    fun readersShareGuardsAndCancellationReleasesThemForWriters(kind: String) {
        val f = isolatedFixture()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'workforce.read')",
                f.company,
                f.employeeAccount,
            )
        val day = LocalDate.parse("2026-10-01")
        val path =
            if (kind == "directory") "${f.path}/employees?asOf=$day"
            else "${f.path}/employees/${f.employee}/calendar/review?from=$day&until=$day"
        val key = "${if (kind == "directory") "people" else "calendar"}:${f.company}"
        val barrier = AccountLockProbe.Barrier(f.actor.accountId)
        val finished = CountDownLatch(1)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    try {
                        if (kind == "directory") directory.execute(f.actor, day, "", null, 50)
                        else review.execute(f.actor, f.employee, day, day)
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val parallel = get(f.employeeClient, path)
                assertEquals(200, parallel.statusCode(), parallel.body())
                assertEquals(
                    false,
                    database()
                        .queryForObject(
                            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                            Boolean::class.java,
                            key,
                        ),
                )
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertEquals(
                    true,
                    database()
                        .queryForObject(
                            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                            Boolean::class.java,
                            key,
                        ),
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        anotherEmployee(f)
        createShift(f)
        assertEquals(200, get(f.admin, path).statusCode())
    }
}
