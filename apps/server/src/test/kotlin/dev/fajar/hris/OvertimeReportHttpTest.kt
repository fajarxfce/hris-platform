package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.domain.repositories.OvertimeReportRepository
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class OvertimeReportHttpTest : OvertimeReportApiFixture() {
    @Autowired private lateinit var reports: OvertimeReportRepository

    @Test
    fun reportsRetainRequestedActualAndApprovedMinutesAcrossStatusesAndOvernightWorkDates() {
        val f = overtimeFixture()
        assertEquals(3, report(f)["months"].size())
        assertEquals(0, report(f)["totals"]["requests"].asLong())
        val approved = pending(f)
        overtimeBody(decide(f, approved))
        fun add(
            start: String,
            end: String,
            actualStart: String? = null,
            actualEnd: String? = null,
            rest: Int = 15,
        ): UUID {
            val id = UUID.randomUUID()
            overtimeBody(
                plan(
                    f,
                    id,
                    changes =
                        mapOf(
                            "requested" to
                                interval("2026-09-01T${start}:00Z", "2026-09-01T${end}:00Z")
                        ),
                )
            )
            if (actualStart != null && actualEnd != null)
                overtimeBody(
                    actual(
                        f,
                        id,
                        changes =
                            mapOf(
                                "actual" to
                                    interval(
                                        "2026-09-01T${actualStart}:00Z",
                                        "2026-09-01T${actualEnd}:00Z",
                                        rest,
                                    )
                            ),
                    )
                )
            return id
        }
        add("04:00", "06:00", "04:15", "05:45")
        add("06:00", "08:00")
        val rejected = add("08:00", "10:00", "08:15", "09:45")
        overtimeBody(decide(f, rejected, decision = "REJECT"))
        overtimeBody(withdraw(f, add("10:00", "11:00"), 0))
        overtimeBody(withdraw(f, add("11:00", "12:00", "11:05", "11:55", 10), 1))
        holiday(f, "2026-08-31")
        val overnight = UUID.randomUUID()
        overtimeBody(
            plan(
                f,
                overnight,
                changes =
                    mapOf(
                        "workDate" to "2026-08-31",
                        "requested" to interval("2026-08-31T16:00:00Z", "2026-08-31T18:00:00Z"),
                    ),
            )
        )
        overtimeBody(
            actual(
                f,
                overnight,
                changes =
                    mapOf("actual" to interval("2026-08-31T16:15:00Z", "2026-08-31T17:45:00Z", 15)),
            )
        )
        overtimeBody(decide(f, overnight))
        holiday(f, "2026-10-01")
        overtimeBody(
            plan(
                f,
                changes =
                    mapOf(
                        "workDate" to "2026-10-01",
                        "requested" to interval("2026-10-01T04:00:00Z", "2026-10-01T06:00:00Z"),
                    ),
            )
        )
        overtimeBody(
            command(
                f.admin,
                "${f.path}/holidays/${f.holiday}",
                """{"workDate":"2026-09-01","name":"Revised calendar","expectedVersion":0,"active":false,"reason":"Calendar revision after evidence"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        val result = report(f)
        assertEquals("overtime.v1", result["definitionVersion"].asString())
        assertTrue(result["status"].isNull)
        assertEquals(
            json.readTree(
                """{"requests":8,"employments":1,"actualRequests":5,"requestedMinutes":900,"actualMinutes":400,"approvedMinutes":210}"""
            ),
            result["totals"],
        )
        val august = result["months"][0]
        assertEquals("2026-08", august["month"].asString())
        assertEquals("2026-08-31", august["from"].asString())
        assertEquals("2026-08-31", august["until"].asString())
        assertEquals(75, august["counts"]["approvedMinutes"].asLong())
        assertEquals(135, result["months"][1]["counts"]["approvedMinutes"].asLong())
        assertEquals("2026-10-01", result["months"][2]["until"].asString())
        assertEquals(1, result["months"][2]["counts"]["requests"].asLong())
        val statuses =
            mapOf(
                "PLANNED" to listOf(2, 0, 240, 0, 0),
                "PENDING" to listOf(1, 1, 120, 75, 0),
                "APPROVED" to listOf(2, 2, 300, 210, 210),
                "REJECTED" to listOf(1, 1, 120, 75, 0),
                "WITHDRAWN" to listOf(2, 1, 120, 40, 0),
            )
        for ((status, values) in statuses) {
            val filtered = report(f, status)
            assertEquals(status, filtered["status"].asString())
            listOf(
                    "requests",
                    "actualRequests",
                    "requestedMinutes",
                    "actualMinutes",
                    "approvedMinutes",
                )
                .forEachIndexed { index, field ->
                    assertEquals(
                        values[index].toLong(),
                        filtered["totals"][field].asLong(),
                        "$status:$field",
                    )
                }
            // Every clipped monthly drill-down uses the existing scoped directory's date/status
            // semantics.
            for (month in filtered["months"]) {
                val rows =
                    overtimeBody(
                        get(
                            f.admin,
                            "${f.path}/overtime?from=${month["from"].asString()}&until=${month["until"].asString()}&status=$status&limit=200",
                        )
                    )["items"]
                assertEquals(month["counts"]["requests"].asLong(), rows.size().toLong())
                assertEquals(
                    month["counts"]["requestedMinutes"].asLong(),
                    rows.sumOf { it["requested"]["workedMinutes"].asLong() },
                )
                assertEquals(
                    month["counts"]["approvedMinutes"].asLong(),
                    rows.sumOf { it["approvedMinutes"].asLong() },
                )
            }
        }
        assertFalse(result.toString().contains("employeeName"))
        assertFalse(result.toString().contains("employeeId"))
        assertFalse(result.toString().contains("reason"))
    }

    @Test
    fun onlyTheLastApprovalStageProducesApprovedMinutesAndOriginalReceiptReplayDoesNotCountTwice() {
        val f = overtimeFixture()
        val second = reviewer(f)
        approvalPolicy(f, listOf(f.actor.accountId, second.id), 0)
        val id = pending(f)
        overtimeBody(decide(f, id))
        assertEquals(135, report(f, "PENDING")["totals"]["actualMinutes"].asLong())
        assertEquals(0, report(f)["totals"]["approvedMinutes"].asLong())
        val key = UUID.randomUUID()
        val receipt = overtimeBody(decide(f, id, 2, key = key, by = second))
        assertEquals(receipt, overtimeBody(decide(f, id, 2, key = key, by = second)))
        assertEquals(1, report(f)["totals"]["requests"].asLong())
        assertEquals(135, report(f, "APPROVED")["totals"]["approvedMinutes"].asLong())
        assertEquals(0, report(f, "PENDING")["totals"]["requests"].asLong())
    }

    @Test
    fun companySourceGrantsDateBoundsAndRowIsolationAreEnforced() {
        val f = overtimeFixture()
        pending(f)
        for (source in listOf("overtime.read", "overtime.manage", "workforce.read")) {
            val member = reviewer(f, setOf("reports.read", source))
            assertEquals(1, report(f, client = member.client)["totals"]["requests"].asLong())
        }
        for (permissions in
            listOf(
                setOf("reports.read"),
                setOf("overtime.read"),
                setOf(
                    "reports.read",
                    "overtime.self.manage",
                    "overtime.team.read",
                    "workforce.team.read",
                ),
                setOf("reports.read", "overtime.approve"),
            )) {
            val member = reviewer(f, permissions)
            assertEquals(403, get(member.client, reportPath(f)).statusCode())
        }
        val other = overtimeFixture()
        val outsider = reviewer(other, setOf("reports.read", "overtime.read"))
        assertEquals(403, get(outsider.client, reportPath(f)).statusCode())
        val isolated =
            transactions.run(other.actor) {
                reports.aggregate(
                    f.company,
                    LocalDate.parse("2026-08-31"),
                    LocalDate.parse("2026-10-01"),
                    null,
                )
            } as Result.Success
        assertEquals(0, isolated.value.totals.requests)
        assertEquals(0, report(other)["totals"]["requests"].asLong())
        for (from in listOf("1999-12-31", "2025-08-31", "2026-10-02")) assertCode(
            get(f.admin, reportPath(f).replace("from=2026-08-31", "from=$from")),
            422,
            "invalid_report_range",
        )
        val leap =
            overtimeBody(
                get(
                    f.admin,
                    reportPath(f)
                        .replace(
                            "from=2026-08-31&until=2026-10-01",
                            "from=2024-01-31&until=2025-01-30",
                        ),
                )
            )
        assertEquals(13, leap["months"].size())
        assertEquals("2024-02-29", leap["months"][1]["until"].asString())
    }

    @Test
    fun reportingAvailabilityProtectsTheNewEndpoint() {
        val f = overtimeFixture()
        overtimeBody(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/settings/client-policy",
                """{"expectedVersion":null,"activateAt":null,"disabledModules":["REPORTING"],"minimumBuilds":{"android":0,"ios":0,"web":0},"maintenance":null,"reason":"Pause reporting"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        assertCode(get(f.admin, reportPath(f)), 403, "company_module_disabled")
    }
}
