package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.jobs.domain.entities.JobStep
import dev.fajar.hris.workforce.domain.usecases.AdvanceWorkPeriodClose
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode

abstract class AttendanceAdjustmentApiFixture : WorkforceAccessApiFixture() {
    @Autowired private lateinit var advance: AdvanceWorkPeriodClose
    protected val adjustmentDate = "2026-09-30"

    protected fun closedFixture(): Fixture = isolatedFixture().also { closeFixture(it) }

    protected fun closeFixture(f: Fixture) {
        val lease = begin(f)
        assertEquals(Result.Success(JobStep(1, false)), advance.execute(f.actor, lease))
        assertEquals(Result.Success(JobStep(2, true)), advance.execute(f.actor, lease))
    }

    protected fun adjustmentPath(f: Fixture, employee: UUID = f.employee) =
        "${f.path}/employees/$employee/attendance/adjustments"

    protected fun adjustmentReview(f: Fixture, date: String = adjustmentDate): JsonNode {
        val response = get(f.admin, "${adjustmentPath(f)}/review?workDate=$date")
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun adjustmentBody(
        review: JsonNode,
        date: String = adjustmentDate,
        absence: Boolean = false,
    ): Map<String, Any?> =
        mapOf(
            "workDate" to date,
            "reviewRevision" to review["revision"].asString(),
            "clockIn" to if (absence) null else "${date}T01:00:00Z",
            "clockOut" to if (absence) null else "${date}T09:00:00Z",
            "breakMinutes" to if (absence) 0 else 60,
            "decisions" to
                review["attendance"]["entries"]
                    .filter { it["status"].asString() == "PENDING" }
                    .map {
                        mapOf(
                            "eventId" to it["id"].asString(),
                            "expectedVersion" to it["version"].asLong(),
                            "decision" to "ACCEPT",
                        )
                    },
            "reason" to "Reviewed late attendance",
        )

    protected fun adjust(
        f: Fixture,
        body: Map<String, Any?>,
        key: UUID = UUID.randomUUID(),
    ): HttpResponse<String> =
        command(f.admin, adjustmentPath(f), json.writeValueAsString(body), f.csrf, key)

    protected fun adjusted(
        f: Fixture,
        body: Map<String, Any?>,
        key: UUID = UUID.randomUUID(),
    ): JsonNode {
        val response = adjust(f, body, key)
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun unchangedFacts(f: Fixture): Map<String, List<String>> =
        listOf("work_periods", "work_period_targets", "work_period_snapshots", "attendance_events")
            .associateWith { table ->
                database()
                    .queryForList(
                        "select to_jsonb(t)::text from $table t where company_id=? order by to_jsonb(t)::text",
                        String::class.java,
                        f.company,
                    )
                    .map { requireNotNull(it) }
            }

    protected fun adjustmentState(f: Fixture): Map<String, List<String>> =
        listOf(
                "attendance_adjustments",
                "attendance_reviews",
                "attendance_corrections",
                "attendance_day_versions",
                "attendance_sync_heads",
                "mobile_sync_changes",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
            .associateWith { table ->
                database()
                    .queryForList(
                        "select to_jsonb(t)::text from $table t where company_id=? order by to_jsonb(t)::text",
                        String::class.java,
                        f.company,
                    )
                    .map { requireNotNull(it) }
            }
}
