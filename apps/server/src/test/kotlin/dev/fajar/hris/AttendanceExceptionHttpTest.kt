package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.repositories.AttendanceRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AttendanceExceptionHttpTest : AttendanceExceptionApiFixture() {
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var attendance: AttendanceRepository

    @Test
    fun committedEvidenceAndReviewDriveOneMinimalExceptionPerEmployeeDay() {
        val f = isolatedFixture()
        val start = recordOfflineEvent(f)
        val pending = exceptionPage(f)["items"].single()
        assertEquals(
            setOf(
                "employeeId",
                "employeeNumber",
                "employeeName",
                "workDate",
                "pendingCount",
                "incomplete",
                "correctionVersion",
                "lastReceivedAt",
            ),
            pending.properties().map { it.key }.toSet(),
        )
        assertEquals(f.employee.toString(), pending["employeeId"].asString())
        assertEquals(1, pending["pendingCount"].asInt())
        assertFalse(pending["incomplete"].asBoolean())
        assertTrue(exceptionPage(f, "INCOMPLETE")["items"].isEmpty)
        reviewEvent(f, start, "ACCEPT")
        val incomplete = exceptionPage(f, "INCOMPLETE")["items"].single()
        assertTrue(incomplete["incomplete"].asBoolean())
        assertEquals(0, incomplete["pendingCount"].asInt())
        assertTrue(exceptionPage(f, "PENDING")["items"].isEmpty)
        val end =
            recordOfflineEvent(f, kind = "CLOCK_OUT", at = Instant.parse("2026-09-30T02:00:00Z"))
        val both = exceptionPage(f)["items"].single()
        assertTrue(both["incomplete"].asBoolean())
        assertEquals(1, both["pendingCount"].asInt())
        assertEquals(1, exceptionPage(f, "PENDING")["items"].size())
        assertEquals(1, exceptionPage(f, "INCOMPLETE")["items"].size())
        reviewEvent(f, end, "ACCEPT")
        assertTrue(exceptionPage(f)["items"].isEmpty)
        val days =
            get(
                f.admin,
                "${f.path}/employees/${f.employee}/attendance?from=2026-09-30&until=2026-09-30",
            )
        assertEquals(200, days.statusCode(), days.body())
        assertEquals(60, json.readTree(days.body())[0]["acceptedMinutes"].asInt())
        assertEquals(2, json.readTree(days.body())[0]["entries"].size())
    }

    @Test
    fun correctionsRemoveRawIncompletenessButNeverHideNewPendingEvidence() {
        val f = isolatedFixture()
        val start = recordOfflineEvent(f)
        reviewEvent(f, start, "ACCEPT")
        val changed =
            command(
                f.admin,
                "${f.path}/employees/${f.employee}/attendance/corrections",
                """{"workDate":"2026-09-30","breakMinutes":0,"reason":"Verified absence"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, changed.statusCode(), changed.body())
        assertTrue(exceptionPage(f)["items"].isEmpty)
        val late = recordOfflineEvent(f, at = Instant.parse("2026-09-30T02:00:00Z"))
        val pending = exceptionPage(f)["items"].single()
        assertEquals(1, pending["pendingCount"].asInt())
        assertFalse(pending["incomplete"].asBoolean())
        assertEquals(0, pending["correctionVersion"].asLong())
        assertTrue(exceptionPage(f, "INCOMPLETE")["items"].isEmpty)
        reviewEvent(f, late, "REJECT")
        assertTrue(exceptionPage(f)["items"].isEmpty)
    }

    @Test
    fun pagesKeepTheirWindowAndFilterAndRemainUsableWhenAnEarlierRowIsResolved() {
        val f = isolatedFixture()
        val original = recordOfflineEvent(f)
        val second = anotherEmployee(f)
        val copy = UUID.randomUUID()
        // The owned fixture copies immutable evidence to exercise a second employee at one date.
        database()
            .update(
                """
                INSERT INTO attendance_events(company_id,id,employment_id,account_id,work_date,kind,captured_at,received_at,device_id,
                    offline,mocked,schedule,initial_status,issues)
                SELECT company_id,?,?,account_id,work_date,kind,captured_at,received_at,device_id,offline,mocked,schedule,initial_status,issues
                FROM attendance_events WHERE company_id=? AND id=?
                """
                    .trimIndent(),
                copy,
                second,
                f.company,
                original,
            )
        recordOfflineEvent(f, date = "2026-09-29")
        val first = exceptionPage(f, limit = 1)
        assertEquals(1, first["items"].size())
        val sameDateEmployees = listOf(f.employee.toString(), second.toString()).sorted()
        assertEquals(sameDateEmployees[0], first["items"][0]["employeeId"].asString())
        val cursor = first["nextCursor"].asString()
        reviewEvent(
            f,
            if (sameDateEmployees[0] == f.employee.toString()) original else copy,
            "REJECT",
        )
        val next = exceptionPage(f, after = cursor, limit = 1)
        assertEquals(sameDateEmployees[1], next["items"][0]["employeeId"].asString())
        val last = exceptionPage(f, after = next["nextCursor"].asString(), limit = 1)
        assertEquals("2026-09-29", last["items"][0]["workDate"].asString())
        assertTrue(last["nextCursor"].isNull)
        val wrongFilter = get(f.admin, queuePath(f, "PENDING", cursor))
        assertEquals(422, wrongFilter.statusCode(), wrongFilter.body())
        val wrongWindow =
            get(f.admin, queuePath(f, after = cursor).replace("from=2026-09-01", "from=2026-09-02"))
        assertEquals(422, wrongWindow.statusCode(), wrongWindow.body())
    }

    @Test
    fun permissionsBoundsAndRlsCannotExposeAnotherCompanyOrItsPrivateEvidence() {
        val f = isolatedFixture()
        recordOfflineEvent(f)
        val self = get(f.employeeClient, queuePath(f))
        assertEquals(403, self.statusCode(), self.body())
        for (path in
            listOf(
                queuePath(f, limit = 0),
                queuePath(f, limit = 201),
                queuePath(f).replace("from=2026-09-01", "from=2026-08-31"),
                queuePath(f, after = "x".repeat(161)),
            )) {
            val response = get(f.admin, path)
            assertEquals(422, response.statusCode(), response.body())
        }
        val other = isolatedFixture()
        assertEquals(403, get(other.admin, queuePath(f)).statusCode())
        assertTrue(exceptionPage(other)["items"].isEmpty)
        val wrongSqlScope =
            transactions.run(other.actor) {
                attendance.exceptions(
                    f.company,
                    LocalDate.parse("2026-09-01"),
                    LocalDate.parse("2026-10-01"),
                    null,
                    null,
                    50,
                )
            }
        assertTrue(wrongSqlScope is Result.Success)
        assertTrue((wrongSqlScope as Result.Success).value.items.isEmpty())
    }
}
