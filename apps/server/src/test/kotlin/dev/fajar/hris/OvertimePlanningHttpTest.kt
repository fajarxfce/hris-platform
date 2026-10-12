package dev.fajar.hris

import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OvertimePlanningHttpTest : OvertimeApiFixture() {
    private fun path(f: Fixture, review: Boolean = false): String =
        "${f.path}/overtime/planning/employees${if (review) "/${f.employee}" else ""}?workDate=2026-09-01"

    @Test
    fun administrativeDiscoveryIsBoundedAndDoesNotGrantProfileOrCalendarAccess() {
        val f = overtimeFixture()
        val foreign = overtimeFixture()
        val reader = reviewer(f, setOf("overtime.manage"))
        val second = UUID.randomUUID()
        val created =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/employees",
                json.writeValueAsString(
                    mapOf(
                        "id" to second,
                        "employeeNumber" to "E002",
                        "person" to
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "legalName" to "Another Employee",
                                "nationality" to "ID",
                            ),
                        "terms" to
                            mapOf(
                                "effectiveFrom" to "2026-01-01",
                                "startDate" to "2026-01-01",
                                "contract" to "PERMANENT",
                                "status" to "ACTIVE",
                            ),
                        "reason" to "Planning discovery",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, created.statusCode(), created.body())
        val first = overtimeBody(get(reader.client, "${path(f)}&limit=1"))
        assertEquals(1, first["items"].size())
        assertEquals(
            setOf("id", "employeeNumber", "legalName", "status", "version"),
            first["items"][0].properties().map { it.key }.toSet(),
        )
        val next =
            overtimeBody(
                get(reader.client, "${path(f)}&limit=1&after=${first["nextCursor"].asString()}")
            )
        assertTrue(next["nextCursor"].isNull)
        assertEquals(
            setOf(f.employee.toString(), second.toString()),
            setOf(first["items"][0]["id"].asString(), next["items"][0]["id"].asString()),
        )
        val filtered = overtimeBody(get(reader.client, "${path(f)}&query=Another"))
        assertEquals(second.toString(), filtered["items"].single()["id"].asString())
        assertEquals(403, get(f.employeeClient, path(f)).statusCode())
        assertCode(
            get(
                f.employeeClient,
                "${f.path}/overtime/planning/employees/$second?workDate=2026-09-01",
            ),
            404,
            "employee_not_found",
        )
        assertEquals(403, get(reader.client, path(foreign)).statusCode())
        assertEquals(
            403,
            get(reader.client, "/api/v1/companies/${f.company}/employees?asOf=2026-09-01")
                .statusCode(),
        )
        assertEquals(
            403,
            get(
                    reader.client,
                    "${f.path}/employees/${f.employee}/calendar/review?from=2026-09-01&until=2026-09-01",
                )
                .statusCode(),
        )
        for (query in
            listOf(
                "limit=0",
                "limit=51",
                "query=${"x".repeat(121)}",
                "after=",
                "after=${"a".repeat(33)}",
            )) assertCode(get(reader.client, "${path(f)}&$query"), 422, "invalid_page")
        for (date in listOf("2026-08-30", "2026-12-31", "1999-12-31", "2101-01-01")) assertCode(
            get(reader.client, "${f.path}/overtime/planning/employees?workDate=$date"),
            422,
            "overtime_date_out_of_range",
        )
        for (date in listOf("2026-08-31", "2026-12-30")) overtimeBody(
            get(reader.client, "${f.path}/overtime/planning/employees?workDate=$date")
        )
    }

    @Test
    fun ownAndAdministrativeReviewsKeepCalendarEvidenceWithoutCreatingPeriodOrOperationRows() {
        val f = overtimeFixture()
        val foreign = overtimeFixture()
        val reader = reviewer(f, setOf("overtime.manage"))
        val approver = reviewer(f)
        val before =
            listOf(
                    "overtime_requests",
                    "overtime_changes",
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                )
                .associateWith { rows(f, it) }
        val periods =
            database()
                .queryForObject(
                    "select count(*) from work_periods where company_id=?",
                    Int::class.java,
                    f.company,
                )
        for (client in listOf(f.employeeClient, reader.client)) {
            val result = overtimeBody(get(client, path(f, true)))
            assertEquals(f.employee.toString(), result["employee"]["id"].asString())
            assertEquals(0, result["employee"]["version"].asInt())
            assertEquals("2026-10-01", result["companyDate"].asString())
            assertEquals("2026-09-01", result["workDate"].asString())
            assertEquals("Asia/Jakarta", result["timezone"].asString())
            assertEquals("OFF", result["schedule"]["kind"].asString())
            assertEquals("HOLIDAY", result["schedule"]["origin"].asString())
            assertEquals(f.holiday.toString(), result["schedule"]["holidayId"].asString())
            assertFalse(result.toString().contains("nationality"))
        }
        assertEquals(403, get(approver.client, path(f, true)).statusCode())
        assertCode(
            get(
                reader.client,
                "${f.path}/overtime/planning/employees/${foreign.employee}?workDate=2026-09-01",
            ),
            404,
            "employee_not_found",
        )
        assertCode(
            get(
                f.employeeClient,
                "${f.path}/overtime/planning/employees/${foreign.employee}?workDate=2026-09-01",
            ),
            404,
            "employee_not_found",
        )
        assertEquals(before, before.keys.associateWith { rows(f, it) })
        assertEquals(
            periods,
            database()
                .queryForObject(
                    "select count(*) from work_periods where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun missingScheduleAndProcessingMonthRemainUnavailable() {
        val f = overtimeFixture()
        assertCode(
            get(
                f.employeeClient,
                "${f.path}/overtime/planning/employees/${f.employee}?workDate=2026-09-02",
            ),
            422,
            "overtime_schedule_missing",
        )
        overtimeBody(close(f))
        assertCode(get(f.employeeClient, path(f, true)), 409, "work_period_locked")
    }

    @Test
    fun planningBoundsUseCompanyDateAcrossMidnight() {
        val f = overtimeFixture()
        clock.set(Instant.parse("2026-10-01T18:00:00Z"))
        val reader = reviewer(f, setOf("overtime.manage"))
        val result = overtimeBody(get(reader.client, path(f, true)))
        assertEquals("2026-10-02", result["companyDate"].asString())
        overtimeBody(
            get(reader.client, "${f.path}/overtime/planning/employees?workDate=2026-12-31")
        )
        assertCode(
            get(reader.client, "${f.path}/overtime/planning/employees?workDate=2026-08-31"),
            422,
            "overtime_date_out_of_range",
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun revokedPermissionMembershipAndCredentialCannotAdmitPendingReads(review: Boolean) {
        for (mode in listOf("permission", "membership", "credential")) {
            val f = overtimeFixture()
            val reader = reviewer(f, setOf("overtime.manage"))
            val barrier = AccountLockProbe.Barrier(reader.id, occurrence = 2)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<HttpResponse<String>> { get(reader.client, path(f, review)) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    when (mode) {
                        "permission" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='overtime.manage'",
                                    f.company,
                                    reader.id,
                                )
                        "membership" ->
                            database()
                                .update(
                                    "update company_memberships set active=false where company_id=? and account_id=?",
                                    f.company,
                                    reader.id,
                                )
                        else ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    reader.id,
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
}
