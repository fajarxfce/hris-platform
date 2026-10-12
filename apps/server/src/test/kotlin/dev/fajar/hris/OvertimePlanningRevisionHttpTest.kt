package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OvertimePlanningRevisionHttpTest : OvertimeApiFixture() {
    private fun revision(f: Fixture): String =
        overtimeBody(
                get(
                    f.employeeClient,
                    "${f.path}/overtime/planning/employees/${f.employee}?workDate=2026-09-01",
                )
            )["revision"]
            .asString()

    private fun reviseHoliday(f: Fixture, version: Int) {
        overtimeBody(
            command(
                f.admin,
                "${f.path}/holidays/${f.holiday}",
                """{"workDate":"2026-09-01","name":"Reviewed holiday $version","expectedVersion":$version,"active":true,"reason":"Calendar reviewed"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
    }

    @Test
    fun changedReviewRejectsWithoutConsumingAnOperationAndFreshReviewRecovers() {
        val f = overtimeFixture()
        val original = revision(f)
        val id = UUID.randomUUID()
        val operation = UUID.randomUUID()
        reviseHoliday(f, 0)
        val before =
            listOf(
                    "overtime_requests",
                    "overtime_changes",
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                )
                .associateWith { rows(f, it) }
        val periodsBefore =
            database()
                .queryForObject(
                    "select count(*) from work_periods where company_id=?",
                    Int::class.java,
                    f.company,
                )
        assertCode(
            plan(f, id, operation, mapOf("planningRevision" to original)),
            409,
            "overtime_planning_changed",
        )
        assertEquals(before, before.keys.associateWith { rows(f, it) })
        assertEquals(
            periodsBefore,
            database()
                .queryForObject(
                    "select count(*) from work_periods where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        val current = revision(f)
        assertNotEquals(original, current)
        val receipt = overtimeBody(plan(f, id, operation, mapOf("planningRevision" to current)))
        assertEquals(0, receipt["version"].asInt())
        assertEquals(1, details(f, id)["request"]["schedule"]["originVersion"].asInt())
    }

    @Test
    fun originalReceiptPrecedesCurrentCalendarComparisonAndBindsTheReviewedRevision() {
        val f = overtimeFixture()
        val original = revision(f)
        val id = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val body = mapOf("planningRevision" to original)
        val receipt = overtimeBody(plan(f, id, operation, body))
        reviseHoliday(f, 0)
        assertEquals(receipt, overtimeBody(plan(f, id, operation, body)))
        assertEquals(1, rows(f, "overtime_requests"))
        assertEquals(1, rows(f, "overtime_changes"))
        assertEquals(0, details(f, id)["request"]["schedule"]["originVersion"].asInt())
        assertCode(
            plan(f, id, operation, mapOf("planningRevision" to revision(f))),
            409,
            "operation_payload_mismatch",
        )
        assertCode(plan(f, id, operation), 409, "operation_payload_mismatch")
    }

    @Test
    fun malformedAndForeignReviewCannotAuthorizeOrCreateAPlan() {
        val f = overtimeFixture()
        val foreign = overtimeFixture()
        val operation = UUID.randomUUID()
        val id = UUID.randomUUID()
        for (value in listOf("", "a".repeat(63), "A".repeat(64), "a".repeat(65))) {
            assertCode(
                plan(f, id, operation, mapOf("planningRevision" to value)),
                422,
                "invalid_overtime_planning_revision",
            )
        }
        assertCode(
            plan(f, id, operation, mapOf("planningRevision" to revision(foreign))),
            409,
            "overtime_planning_changed",
        )
        assertEquals(0, rows(f, "overtime_requests"))
        assertEquals(0, rows(f, "overtime_changes"))
        overtimeBody(plan(f, id, operation, mapOf("planningRevision" to revision(f))))
    }
}
