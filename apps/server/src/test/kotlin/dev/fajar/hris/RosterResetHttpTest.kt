package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
class RosterResetHttpTest : RosterResetApiFixture() {
    @Test
    fun resettingAnOpenDayDoesNotRewriteCapturedAttendanceEvidence() {
        val f = isolatedFixture()
        body(roster(f, shift = createShift(f)))
        clock.set(Instant.parse("2026-10-02T03:00:00Z"))
        val event = UUID.randomUUID()
        body(
            command(
                f.employeeClient,
                "${f.path}/employees/${f.employee}/attendance",
                json.writeValueAsString(
                    mapOf(
                        "id" to event,
                        "deviceId" to UUID.randomUUID(),
                        "workDate" to workDate,
                        "kind" to "CLOCK_IN",
                        "capturedAt" to "2026-10-02T01:00:00Z",
                        "offline" to true,
                    )
                ),
                f.employeeCsrf,
                UUID.randomUUID(),
            )
        )
        val frozen =
            database()
                .queryForObject(
                    "select schedule::text from attendance_events where company_id=? and id=?",
                    String::class.java,
                    f.company,
                    event,
                )
        body(reset(f))
        assertEquals(
            frozen,
            database()
                .queryForObject(
                    "select schedule::text from attendance_events where company_id=? and id=?",
                    String::class.java,
                    f.company,
                    event,
                ),
        )
        assertEquals("ROSTER", json.readTree(frozen)["origin"].asString())
        assertEquals("UNASSIGNED", review(f)["calendar"]["days"][0]["kind"].asString())
    }

    @Test
    fun anOverrideCannotBeWithdrawnIntoANonexistentLocalShiftTime() {
        val f = isolatedFixture()
        val shift = createShift(f)
        val shiftInput = json.readTree(shiftBody(shift)) as tools.jackson.databind.node.ObjectNode
        shiftInput
            .put("expectedVersion", 0)
            .put("timezone", "America/New_York")
            .put("startsAt", "02:30")
            .put("endsAt", "10:30")
        body(
            command(
                f.admin,
                "${f.path}/shifts/$shift",
                json.writeValueAsString(shiftInput),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        body(
            command(
                f.admin,
                "${f.path}/employees/${f.employee}/schedule",
                """{"effectiveFrom":"2026-01-01","days":{"SUNDAY":{"id":"$shift","version":1}},"reason":"Weekly schedule"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        val day = "2026-03-08"
        body(roster(f, shift = createShift(f), day = day))
        val response = reset(f, day = day)
        assertEquals(422, response.statusCode())
        assertEquals("shift_timezone_gap", json.readTree(response.body())["code"].asString())
        assertEquals(0, review(f, day)["rosterOverrides"][0]["version"].asLong())
        assertTrue(review(f, day)["rosterOverrides"][0]["active"].asBoolean())
    }

    @Test
    fun resetRetainsIdentityHistoryAndReviewedVersionsWhileRestoringTheFrozenPattern() {
        val f = isolatedFixture()
        val shift = createShift(f)
        pattern(f, shift)
        body(roster(f))
        val original = row(f)
        assertEquals("ROSTER", review(f)["calendar"]["days"][0]["origin"].asString())
        val key = UUID.randomUUID()
        val first = body(reset(f, operation = key))
        assertEquals(1, first["version"].asLong())
        assertEquals(first, body(reset(f, operation = key)))
        assertEquals(original["id"], row(f)["id"])
        assertEquals(false, row(f)["active"])
        assertEquals(listOf(true, false), revisions(f).map { it["active"] })
        val restored = review(f)
        val day = restored["calendar"]["days"][0]
        assertEquals("PATTERN", day["origin"].asString())
        assertEquals("WORK", day["kind"].asString())
        assertEquals(shift.toString(), day["shift"]["id"].asString())
        assertEquals(1, restored["rosterOverrides"][0]["version"].asLong())
        assertFalse(restored["rosterOverrides"][0]["active"].asBoolean())

        // An old edit cannot recreate an override as version zero after a reset.
        assertEquals(409, roster(f).statusCode())
        assertEquals(409, roster(f, 0, shift).statusCode())
        assertEquals(2, body(roster(f, 1, shift))["version"].asLong())
        assertEquals(first, body(reset(f, operation = key)))
        assertEquals(true, row(f)["active"])
        assertEquals(2L, row(f)["version"])
        assertEquals(listOf(true, false, true), revisions(f).map { it["active"] })
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=? and action='workforce.roster_reset'",
                    Int::class.java,
                    f.company,
                ),
        )
        assertThrows(Exception::class.java) {
            database()
                .update(
                    "delete from roster_revisions where company_id=? and employment_id=?",
                    f.company,
                    f.employee,
                )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["holiday", "unassigned"])
    fun resetRestoresCalendarPrecedenceWithoutCreatingAnExplicitDayOff(mode: String) {
        val f = isolatedFixture()
        if (mode == "holiday") createHoliday(f)
        body(roster(f, shift = createShift(f)))
        body(reset(f))
        val day = review(f)["calendar"]["days"][0]
        if (mode == "holiday") {
            assertEquals("HOLIDAY", day["origin"].asString())
            assertEquals("OFF", day["kind"].asString())
        } else assertEquals("UNASSIGNED", day["kind"].asString())
    }

    @Test
    fun resetPublishesAScopedDeletionAndReactivationUsesTheSameResourceAtAHigherVersion() {
        val f = isolatedFixture()
        body(roster(f))
        val other = isolatedFixture()
        body(roster(other))
        val sync = "/api/v1/companies/${f.company}/sync"
        val query = "collections=ROSTER_DAYS&limit=100"
        val worker = mobileSyncTestWorker(postgres.jdbcUrl, database())
        repeat(4) { assertTrue(worker.maintain.execute() is Result.Success) }
        val initial = body(get(f.employeeClient, "$sync/bootstrap?$query"))
        assertEquals(1, initial["items"].size())
        val id = initial["items"][0]["id"].asString()
        body(reset(f))
        repeat(4) { assertTrue(worker.maintain.execute() is Result.Success) }
        val cursor = URLEncoder.encode(initial["changesCursor"].asString(), Charsets.UTF_8)
        val changed = body(get(f.employeeClient, "$sync/changes?$query&cursor=$cursor"))
        assertEquals(1, changed["items"].size())
        assertEquals("DELETE", changed["items"][0]["operation"].asString())
        assertEquals(id, changed["items"][0]["id"].asString())
        assertEquals(1, changed["items"][0]["version"].asLong())
        assertEquals(0, body(get(f.employeeClient, "$sync/bootstrap?$query"))["items"].size())
        val absent = get(f.employeeClient, "${f.path}/me/roster/$id")
        assertEquals(404, absent.statusCode())
        assertEquals("roster_day_not_found", json.readTree(absent.body())["code"].asString())
        assertEquals(404, get(other.employeeClient, "${other.path}/me/roster/$id").statusCode())
        body(roster(f, 1))
        repeat(4) { assertTrue(worker.maintain.execute() is Result.Success) }
        val next = URLEncoder.encode(changed["cursor"].asString(), Charsets.UTF_8)
        val active = body(get(f.employeeClient, "$sync/changes?$query&cursor=$next"))
        assertEquals(1, active["items"].size())
        assertEquals("UPSERT", active["items"][0]["operation"].asString())
        assertEquals(id, active["items"][0]["id"].asString())
        assertEquals(2, body(get(f.employeeClient, "${f.path}/me/roster/$id"))["version"].asLong())
    }

    @Test
    fun resetRequiresCurrentScopeCsrfAndExactObservedVersion() {
        val f = isolatedFixture()
        body(roster(f))
        val path = "${f.path}/employees/${f.employee}/roster/$workDate/reset"
        val input = """{"expectedVersion":0,"reason":"Restore calendar"}"""
        assertEquals(403, command(f.admin, path, input, "invalid", UUID.randomUUID()).statusCode())
        assertEquals(
            403,
            command(f.employeeClient, path, input, f.employeeCsrf, UUID.randomUUID()).statusCode(),
        )
        val other = isolatedFixture()
        assertEquals(
            403,
            command(other.admin, path, input, other.csrf, UUID.randomUUID()).statusCode(),
        )
        assertEquals(409, reset(f, 2).statusCode())
        for (invalid in listOf("-1", Long.MAX_VALUE.toString(), "0.5", "null")) {
            assertEquals(
                if (invalid == "-1" || invalid == Long.MAX_VALUE.toString()) 422 else 400,
                command(
                        f.admin,
                        path,
                        """{"expectedVersion":$invalid,"reason":"Restore calendar"}""",
                        f.csrf,
                        UUID.randomUUID(),
                    )
                    .statusCode(),
                invalid,
            )
        }
        for (missing in listOf("""{"reason":"Restore calendar"}""", """{"expectedVersion":0}""")) {
            assertEquals(
                400,
                command(f.admin, path, missing, f.csrf, UUID.randomUUID()).statusCode(),
            )
        }
        assertEquals(0L, row(f)["version"])
        val key = UUID.randomUUID()
        body(reset(f, operation = key))
        val already = reset(f, 1)
        assertEquals(409, already.statusCode())
        assertEquals("roster_override_required", json.readTree(already.body())["code"].asString())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='workforce.manage'",
                f.company,
                f.actor.accountId,
            )
        assertEquals(403, reset(f, operation = key).statusCode())
        assertEquals(1L, row(f)["version"])
    }

    @Test
    fun resetCannotModifyAClosingMonthButAnExistingReceiptRemainsReplayable() {
        val f = isolatedFixture()
        val first = "2026-09-03"
        val second = "2026-09-04"
        body(roster(f, day = first))
        body(roster(f, day = second))
        val key = UUID.randomUUID()
        val committed = body(reset(f, operation = key, day = first))
        body(close(f))
        assertEquals(409, reset(f, day = second).statusCode())
        assertEquals(committed, body(reset(f, operation = key, day = first)))
        assertEquals(
            true,
            database()
                .queryForObject(
                    "select active from roster_days where company_id=? and employment_id=? and work_date=?::date",
                    Boolean::class.java,
                    f.company,
                    f.employee,
                    second,
                ),
        )
    }

    @Test
    fun competingResetAndRosterEditApplyOnlyOneReviewedVersion() {
        val f = isolatedFixture()
        val shift = createShift(f)
        body(roster(f))
        val ready = CountDownLatch(2)
        val release = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val reset =
                pool.submit<Int> {
                    ready.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    reset(f).statusCode()
                }
            val save =
                pool.submit<Int> {
                    ready.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    roster(f, 0, shift).statusCode()
                }
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                release.countDown()
                assertEquals(
                    listOf(200, 409),
                    listOf(reset.get(10, TimeUnit.SECONDS), save.get(10, TimeUnit.SECONDS)).sorted(),
                )
            } finally {
                release.countDown()
            }
        }
        assertEquals(1L, row(f)["version"])
        assertEquals(2, revisions(f).size)
    }
}
