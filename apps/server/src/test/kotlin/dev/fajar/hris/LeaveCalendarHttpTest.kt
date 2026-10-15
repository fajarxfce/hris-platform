package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.net.http.HttpClient
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LeaveCalendarHttpTest : LeaveAttachmentApiFixture() {
    @Test
    fun calendarUsesChargedWorkDatesAndRetainedLabelsWithoutPrivateRequestFields() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "5"))
        val id = UUID.randomUUID()
        body(
            submit(
                f,
                id,
                listOf("2026-10-02" to "FIRST_HALF", "2026-10-03" to "FULL", "2026-10-05" to "FULL"),
            )
        )
        val month = body(calendar(f))
        assertEquals("2026-10", month["month"].asString())
        assertEquals("2026-10-01", month["companyDate"].asString())
        assertEquals("Asia/Jakarta", month["timezone"].asString())
        assertEquals(
            listOf("2026-10-02", "2026-10-05"),
            month["days"].toList().map { it["date"].asString() },
        )
        assertTrue(
            month["days"].all {
                it["employeeCount"].asInt() == 1 && it["pendingCount"].asInt() == 1
            }
        )
        val friday = body(entries(f, "2026-10-02"))
        assertEquals("2026-10-02", friday["date"].asString())
        val entry = friday["entries"]["items"].single()
        assertEquals(id.toString(), entry["requestId"].asString())
        assertEquals("FIRST_HALF", entry["portion"].asString())
        assertEquals("Annual leave", entry["typeName"].asString())
        assertEquals(
            "FULL",
            body(entries(f, "2026-10-05"))["entries"]["items"].single()["portion"].asString(),
        )
        assertEquals(0, body(entries(f, "2026-10-03"))["entries"]["items"].size())
        assertEquals(
            setOf(
                "requestId",
                "employeeId",
                "employeeNumber",
                "employeeName",
                "typeCode",
                "typeName",
                "portion",
                "status",
                "version",
            ),
            entry.propertyNames().toSet(),
        )
        body(policy(f, version = 0, paid = false))
        assertEquals(month["days"], body(calendar(f))["days"])
        assertEquals(entry, body(entries(f, "2026-10-02"))["entries"]["items"].single())
    }

    @Test
    fun twoHalfRequestsCountOneEmployeeAndCursorSurvivesWithdrawalOfItsAnchor() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "3"))
        val first = UUID.fromString("00000000-0000-4000-8000-000000000001")
        val second = UUID.fromString("00000000-0000-4000-8000-000000000002")
        body(submit(f, first, listOf("2026-10-05" to "FIRST_HALF")))
        body(submit(f, second, listOf("2026-10-05" to "SECOND_HALF")))
        body(decide(f, second, 0))
        val summary = body(calendar(f))["days"].single()
        assertEquals(1, summary["employeeCount"].asInt())
        assertEquals(2, summary["requestCount"].asInt())
        assertEquals(1, summary["pendingCount"].asInt())
        assertEquals(1, summary["approvedCount"].asInt())
        val page = body(entries(f, "2026-10-05", suffix = "&limit=1"))["entries"]
        assertEquals(first.toString(), page["items"].single()["requestId"].asString())
        assertEquals(first.toString(), page["nextCursor"].asString())
        body(action(f, first, "withdraw", 0))
        val next = body(entries(f, "2026-10-05", suffix = "&after=$first&limit=1"))["entries"]
        assertEquals(second.toString(), next["items"].single()["requestId"].asString())
        assertTrue(next["nextCursor"].isNull)
        assertEquals(1, body(calendar(f))["days"].single()["requestCount"].asInt())
    }

    @Test
    fun approvedCancellationAloneRemovesOccupancyAndRejectedRequestsDisappear() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "5"))
        val approved = UUID.randomUUID()
        val rejected = UUID.randomUUID()
        body(submit(f, approved, listOf("2026-10-05" to "FULL")))
        body(submit(f, rejected, listOf("2026-10-06" to "FULL")))
        body(decide(f, approved, 0))
        body(decide(f, rejected, 0, "REJECT"))
        body(action(f, approved, "cancellation", 1))
        val waiting = body(calendar(f))["days"].single()
        assertEquals("2026-10-05", waiting["date"].asString())
        assertEquals(1, waiting["cancellationPendingCount"].asInt())
        assertEquals(
            "CANCELLATION_PENDING",
            body(entries(f, "2026-10-05"))["entries"]["items"].single()["status"].asString(),
        )
        body(decide(f, approved, 2))
        assertEquals(0, body(calendar(f))["days"].size())
        assertEquals(0, body(entries(f, "2026-10-05"))["entries"]["items"].size())
    }

    @Test
    fun teamCalendarUsesCurrentDirectReportsEvenWhenViewingAnEarlierMonth() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        body(submit(f, UUID.randomUUID(), listOf("2026-10-05" to "FULL")))
        assertEquals(1, body(calendar(f, f.supervisor, "TEAM"))["days"].size())
        body(
            revise(
                f.admin,
                f.adminCsrf,
                f.company,
                f.employee,
                0,
                terms(from = "2026-10-02", manager = null, start = "2025-01-01"),
            )
        )
        assertEquals(1, body(calendar(f, f.supervisor, "TEAM"))["days"].size())
        clock.set(Instant.parse("2026-11-01T00:00:00Z"))
        login(f.supervisor, "${f.managerAccount}@example.test")
        login(f.admin)
        assertEquals(0, body(calendar(f, f.supervisor, "TEAM"))["days"].size())
        assertEquals(
            0,
            body(entries(f, "2026-10-05", f.supervisor, "TEAM"))["entries"]["items"].size(),
        )
        assertEquals(1, body(calendar(f))["days"].size())
    }

    @Test
    fun calendarHasItsOwnCompanyAndTeamAuthorization() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        body(submit(f, UUID.randomUUID(), listOf("2026-10-05" to "FULL")))
        error(calendar(f, f.worker, "TEAM"), 403, "access_denied")
        error(calendar(f, f.supervisor, "COMPANY"), 403, "access_denied")
        error(entries(f, "2026-10-05", f.supervisor, "COMPANY"), 403, "access_denied")
        val (_, reader) = member(f, listOf("leave.read"))
        assertEquals(1, body(calendar(f, reader))["days"].size())
        assertEquals(1, body(entries(f, "2026-10-05", reader))["entries"]["items"].size())
        assertEquals(0, body(calendar(f, reader, "TEAM"))["days"].size())
        val (_, approver) = member(f, listOf("leave.approve", "approvals.read"))
        error(calendar(f, approver, "TEAM"), 403, "access_denied")
        val other = company(f.admin, f.adminCsrf)
        val foreign = f.copy(company = other)
        assertEquals(0, body(calendar(foreign))["days"].size())
        assertEquals(0, body(entries(foreign, "2026-10-05"))["entries"]["items"].size())
        assertEquals(403, calendar(foreign, f.supervisor, "TEAM").statusCode())
    }

    @Test
    fun independentLeaveModuleAdmissionDoesNotRequirePeopleOrWorkforceScreens() {
        val f = leaveFixture()
        body(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/settings/client-policy",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to null,
                        "activateAt" to null,
                        "disabledModules" to listOf("PEOPLE", "WORKFORCE"),
                        "minimumBuilds" to mapOf("android" to 0, "ios" to 0, "web" to 0),
                        "maintenance" to null,
                        "reason" to "Calendar admission test",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        assertEquals(200, calendar(f, f.supervisor, "TEAM").statusCode())
        assertEquals(200, entries(f, "2026-10-05", f.supervisor, "TEAM").statusCode())
    }

    @Test
    fun defaultMonthAndCompanyDateFollowCompanyTimezone() {
        val f = leaveFixture(Instant.parse("2026-09-30T18:30:00Z"))
        val view = body(get(f.admin, "/api/v1/companies/${f.company}/leave/calendar?scope=COMPANY"))
        assertEquals("2026-10", view["month"].asString())
        assertEquals("2026-10-01", view["companyDate"].asString())
        assertEquals(0, view["days"].size())
    }

    @ParameterizedTest
    @ValueSource(strings = ["1899-12", "2201-01"])
    fun monthRangeIsBounded(month: String) {
        val f = leaveFixture()
        error(
            get(
                f.admin,
                "/api/v1/companies/${f.company}/leave/calendar?scope=COMPANY&month=$month",
            ),
            422,
            "invalid_leave_calendar_month",
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 201])
    fun dateEntriesHaveBoundedPages(limit: Int) {
        val f = leaveFixture()
        error(entries(f, "2026-10-05", suffix = "&limit=$limit"), 422, "invalid_page")
        error(entries(f, "2201-01-01"), 422, "invalid_leave_calendar_date")
    }

    private fun calendar(
        f: LeaveFixture,
        browser: HttpClient = f.admin,
        scope: String = "COMPANY",
    ) = get(browser, "/api/v1/companies/${f.company}/leave/calendar?scope=$scope&month=2026-10")

    private fun entries(
        f: LeaveFixture,
        date: String,
        browser: HttpClient = f.admin,
        scope: String = "COMPANY",
        suffix: String = "",
    ) =
        get(
            browser,
            "/api/v1/companies/${f.company}/leave/calendar/entries?scope=$scope&date=$date$suffix",
        )
}
