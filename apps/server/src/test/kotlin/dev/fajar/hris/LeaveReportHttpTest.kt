package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.repositories.LeaveReportRepository
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class LeaveReportHttpTest : LeaveReportApiFixture() {
    @Autowired private lateinit var reports: LeaveReportRepository

    @Test
    fun clippedFrozenDatesReconcileAcrossEveryStatusWithoutTreatingReleasedDaysAsActiveLeave() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "30"))
        val empty = report(f)
        assertEquals("leave.v1", empty["definitionVersion"].asString())
        assertEquals(5, empty["statuses"].size())
        assertEquals("0", empty["totals"]["days"].asString())
        val pending = UUID.randomUUID()
        body(
            submit(
                f,
                pending,
                listOf("2026-10-05" to "FULL", "2026-10-06" to "FIRST_HALF", "2026-10-10" to "FULL"),
            )
        )
        val approved = UUID.randomUUID()
        body(submit(f, approved, listOf("2026-10-07" to "FIRST_HALF")))
        body(decide(f, approved, 0))
        val cancellation = UUID.randomUUID()
        body(submit(f, cancellation, listOf("2026-10-08" to "FULL")))
        body(decide(f, cancellation, 0))
        body(action(f, cancellation, "cancellation", 1))
        val rejected = UUID.randomUUID()
        body(submit(f, rejected, listOf("2026-10-09" to "FULL")))
        body(decide(f, rejected, 0, "REJECT"))
        val cancelled = UUID.randomUUID()
        body(submit(f, cancelled, listOf("2026-10-06" to "SECOND_HALF")))
        body(action(f, cancelled, "withdraw", 0))
        body(submit(f, UUID.randomUUID(), listOf("2026-10-12" to "FULL")))
        // Subsequent policy and calendar changes must not reinterpret the submitted working days.
        body(policy(f, 0, paid = false, active = false))
        body(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/workforce/employees/${f.employee}/roster/2026-10-06",
                """{"reason":"Roster change"}""",
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        val value = report(f)
        assertEquals(5, value["totals"]["requests"].asLong())
        assertEquals(1, value["totals"]["employments"].asLong())
        assertEquals("3.5", value["totals"]["days"].asString())
        val expected =
            mapOf(
                "PENDING" to "0.5",
                "APPROVED" to "0.5",
                "REJECTED" to "1",
                "CANCELLED" to "0.5",
                "CANCELLATION_PENDING" to "1",
            )
        for ((status, days) in expected) {
            assertEquals(days, counts(value, status)["days"].asString())
            assertEquals(1, counts(value, status)["requests"].asLong())
            assertEquals(1, counts(value, status)["employments"].asLong())
            val rows = requests(f, status)
            assertEquals(f.company.toString(), rows["companyId"].asString())
            assertEquals(status, rows["status"].asString())
            assertEquals(1, rows["items"].size())
            assertEquals(days, rows["items"][0]["days"].asString())
            assertTrue(rows["nextCursor"].isNull)
        }
        val frozen = requests(f, "PENDING")["items"].single()
        assertEquals(pending.toString(), frozen["id"].asString())
        assertEquals("1.5", frozen["requestDays"].asString())
        assertEquals(2, details(f, pending)["days"].size())
        assertEquals(5, requests(f)["items"].size())
        assertFalse(value.toString().contains("employeeName"))
        assertFalse(value.toString().contains("reason"))
        assertEquals("1.5", balance(f)["consumedDays"].asString())
        body(decide(f, cancellation, 2))
        val refunded = report(f)
        assertEquals(value["totals"], refunded["totals"])
        assertEquals("0", counts(refunded, "CANCELLATION_PENDING")["days"].asString())
        assertEquals("1.5", counts(refunded, "CANCELLED")["days"].asString())
        assertEquals("0.5", balance(f)["consumedDays"].asString())
    }

    @Test
    fun continuationSurvivesAStatusChangeInItsLastRowAndRejectsDifferentFilters() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "5"))
        val ids =
            listOf(
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
            )
        ids.forEachIndexed { index, id ->
            body(submit(f, id, listOf("2026-10-0${6+index}" to "FULL")))
        }
        val first = requests(f, "PENDING", limit = 1)
        assertEquals(ids[0].toString(), first["items"].single()["id"].asString())
        val cursor = first["nextCursor"].asString()
        body(action(f, ids[0], "withdraw", 0))
        val next = requests(f, "PENDING", cursor, 1)
        assertEquals(ids[1].toString(), next["items"].single()["id"].asString())
        assertTrue(next["nextCursor"].isNull)
        error(get(f.admin, reportPath(f, true, "CANCELLED", cursor)), 422, "invalid_page")
        error(
            get(
                f.admin,
                reportPath(f, true, "PENDING", cursor)
                    .replace("until=2026-10-09", "until=2026-10-08"),
            ),
            422,
            "invalid_page",
        )
        error(get(f.admin, reportPath(f, true, limit = 201)), 422, "invalid_page")
        error(get(f.admin, reportPath(f, true, limit = 0)), 422, "invalid_page")
    }

    @Test
    fun reportAndRowScopeRangesAndSourceGrantsFailClosed() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        body(submit(f, UUID.randomUUID(), listOf("2026-10-06" to "FULL")))
        val other = leaveFixture()
        val otherActor = documentFixture(other).actor
        val from = LocalDate.parse("2026-10-06")
        val until = LocalDate.parse("2026-10-09")
        assertEquals(
            0,
            (transactions.run(otherActor) { reports.aggregate(f.company, from, until) }
                    as Result.Success)
                .value
                .totals
                .requests,
        )
        assertTrue(
            (transactions.run(otherActor) {
                    reports.requests(f.company, from, until, null, null, 1)
                } as Result.Success)
                .value
                .items
                .isEmpty()
        )
        for (rows in listOf(false, true)) {
            assertEquals(403, get(f.worker, reportPath(f, rows)).statusCode())
            assertEquals(403, get(f.supervisor, reportPath(f, rows)).statusCode())
            val (_, otherMember) = member(other, listOf("reports.read", "leave.read"))
            assertEquals(403, get(otherMember, reportPath(f, rows)).statusCode())
            for (grants in listOf(listOf("reports.read"), listOf("leave.read"))) {
                val (_, client) = member(f, grants)
                assertEquals(403, get(client, reportPath(f, rows)).statusCode())
            }
            for (start in listOf("2025-10-01", "2026-10-10", "1899-12-31")) error(
                get(f.admin, reportPath(f, rows).replace("from=2026-10-06", "from=$start")),
                422,
                "invalid_report_range",
            )
        }
        assertEquals(0, report(other)["totals"]["requests"].asLong())
        assertEquals(1, report(f)["totals"]["requests"].asLong())
    }

    @Test
    fun datesBetweenRequestedDaysDoNotCountEvenWhenTheRequestEnvelopeOverlaps() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "5"))
        body(submit(f, UUID.randomUUID(), listOf("2026-10-05" to "FULL", "2026-10-09" to "FULL")))
        for (rows in listOf(false, true)) {
            val result =
                body(
                    get(
                        f.admin,
                        reportPath(f, rows).replace("until=2026-10-09", "until=2026-10-08"),
                    )
                )
            if (rows) assertTrue(result["items"].isEmpty)
            else assertEquals("0", result["totals"]["days"].asString())
        }
    }

    @Test
    fun reportingClientPolicyCoversAggregateAndDrillDown() {
        val f = leaveFixture()
        body(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/settings/client-policy",
                """{"expectedVersion":null,"activateAt":null,"disabledModules":["REPORTING"],"minimumBuilds":{"android":0,"ios":0,"web":0},"maintenance":null,"reason":"Pause reports"}""",
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        for (rows in listOf(false, true)) error(
            get(f.admin, reportPath(f, rows)),
            403,
            "company_module_disabled",
        )
    }
}
