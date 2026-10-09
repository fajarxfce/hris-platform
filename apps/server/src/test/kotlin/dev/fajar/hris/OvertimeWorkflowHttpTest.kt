package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OvertimeWorkflowHttpTest : OvertimeApiFixture() {
    @Test
    fun stagedReviewRetainsRequestedActualApprovedTimeAndOriginalReceipts() {
        val f = overtimeFixture()
        val next = reviewer(f)
        approvalPolicy(f, listOf(f.actor.accountId, next.id), 0)
        val id = UUID.randomUUID()
        val plannedKey = UUID.randomUUID()
        val actualKey = UUID.randomUUID()
        val planned = overtimeBody(plan(f, id, plannedKey))
        assertEquals(0, planned["version"].asLong())
        assertEquals(180, details(f, id)["request"]["requested"]["workedMinutes"].asInt())
        val changed =
            command(
                f.admin,
                "${f.path}/holidays/${f.holiday}",
                """{"workDate":"2026-09-01","name":"Updated holiday","expectedVersion":0,"active":false,"reason":"Calendar revised"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        overtimeBody(changed)
        val submitted = overtimeBody(actual(f, id, actualKey))
        assertEquals(1, submitted["version"].asLong())
        var view = details(f, id)
        assertEquals("OFF", view["request"]["schedule"]["kind"].asString())
        assertEquals(135, view["request"]["actual"]["workedMinutes"].asInt())
        assertEquals(0, view["request"]["approvedMinutes"].asInt())
        overtimeBody(decide(f, id))
        view = details(f, id)
        assertEquals("PENDING", view["request"]["status"].asString())
        assertEquals(1, view["approval"]["currentStep"].asInt())
        val key = UUID.randomUUID()
        val completed = overtimeBody(decide(f, id, 2, key = key, by = next))
        assertEquals(3, completed["version"].asLong())
        view = details(f, id)
        assertEquals("APPROVED", view["request"]["status"].asString())
        assertEquals(135, view["request"]["approvedMinutes"].asInt())
        assertEquals(4, view["history"]["items"].size())
        assertEquals(planned, overtimeBody(plan(f, id, plannedKey)))
        assertEquals(submitted, overtimeBody(actual(f, id, actualKey)))
        assertEquals(completed, overtimeBody(decide(f, id, 2, key = key, by = next)))
        assertEquals(2, rows(f, "approval_decisions"))
        assertEquals(4, rows(f, "mobile_sync_changes"))
        assertCode(
            plan(f, id, plannedKey, changes = mapOf("reason" to "Changed payload")),
            409,
            "operation_payload_mismatch",
        )
    }

    @Test
    fun closingWaitsForEveryOvertimeDecisionAndFreezesApprovedFacts() {
        val f = overtimeFixture()
        val id = UUID.randomUUID()
        overtimeBody(plan(f, id))
        assertCode(close(f), 409, "overtime_resolution_required")
        assertEquals(0, rows(f, "background_jobs"))
        overtimeBody(actual(f, id))
        assertCode(close(f), 409, "overtime_resolution_required")
        overtimeBody(decide(f, id))
        val lease = begin(f)
        assertTrue(advance.execute(f.actor, lease) is Result.Success)
        assertTrue(advance.execute(f.actor, lease) is Result.Success)
        val snapshot =
            overtimeBody(get(f.admin, "${f.path}/periods/2026-09/employees/${f.employee}"))
        val day =
            snapshot["days"].iterator().asSequence().single {
                it["workDate"].asString() == "2026-09-01"
            }
        assertEquals(135, day["overtime"][0]["approvedMinutes"].asInt())
        assertEquals(id.toString(), day["overtime"][0]["requestId"].asString())
        assertEquals(2, day["overtime"][0]["revision"].asLong())
        assertCode(withdraw(f, id, 2), 409, "work_period_locked")
        assertCode(
            plan(
                f,
                changes =
                    mapOf("requested" to interval("2026-09-01T05:00:00Z", "2026-09-01T06:00:00Z")),
            ),
            409,
            "work_period_locked",
        )
        assertEquals(1, rows(f, "work_period_snapshots"))
    }

    @Test
    fun overlappingPlansSerializeWhileLostResponsesReplayOneRequest() {
        val f = overtimeFixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        Executors.newFixedThreadPool(2).use { pool ->
            val calls =
                (1..2).map { pool.submit<java.net.http.HttpResponse<String>> { plan(f, id, key) } }
            val bodies = calls.map { overtimeBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(bodies[0], bodies[1])
        }
        assertEquals(1, rows(f, "overtime_requests"))
        assertEquals(1, rows(f, "overtime_changes"))
        assertCode(plan(f), 409, "overtime_overlap")
        overtimeBody(withdraw(f, id, 0))
        Executors.newFixedThreadPool(2).use { pool ->
            val calls = (1..2).map { pool.submit<java.net.http.HttpResponse<String>> { plan(f) } }
            assertEquals(
                listOf(200, 409),
                calls.map { it.get(10, TimeUnit.SECONDS).statusCode() }.sorted(),
            )
        }
        assertEquals(2, rows(f, "overtime_requests"))
    }

    @Test
    fun invalidRealisationsNeverConsumeTheirOperationOrCreateAnApproval() {
        val f = overtimeFixture()
        val id = UUID.randomUUID()
        overtimeBody(plan(f, id))
        val key = UUID.randomUUID()
        assertCode(
            actual(
                f,
                id,
                key,
                mapOf("actual" to interval("2026-09-01T00:00:00Z", "2026-09-01T03:00:00Z")),
            ),
            422,
            "overtime_outside_requested_window",
        )
        assertCode(
            actual(
                f,
                id,
                key,
                mapOf("actual" to interval("2026-09-01T01:00:00Z", "2026-09-01T01:15:00Z", 15)),
            ),
            422,
            "invalid_overtime_interval",
        )
        assertEquals(0, rows(f, "approval_requests"))
        assertEquals("PLANNED", details(f, id)["request"]["status"].asString())
        overtimeBody(actual(f, id, key))
        assertEquals(1, rows(f, "approval_requests"))
        val future = UUID.randomUUID()
        val holiday = UUID.randomUUID()
        overtimeBody(
            command(
                f.admin,
                "${f.path}/holidays/$holiday",
                """{"workDate":"2026-10-02","name":"Future rest day","reason":"Schedule"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        overtimeBody(
            plan(
                f,
                future,
                changes =
                    mapOf(
                        "workDate" to "2026-10-02",
                        "requested" to interval("2026-10-02T01:00:00Z", "2026-10-02T04:00:00Z"),
                    ),
            )
        )
        assertCode(
            actual(
                f,
                future,
                changes =
                    mapOf("actual" to interval("2026-10-02T01:00:00Z", "2026-10-02T03:00:00Z")),
            ),
            422,
            "overtime_not_ended",
        )
    }

    @Test
    fun plannedAndSubmittedWithdrawalsReleaseOccupancyWithoutDeletingEvidence() {
        val f = overtimeFixture()
        val first = UUID.randomUUID()
        overtimeBody(plan(f, first))
        overtimeBody(withdraw(f, first, 0))
        val id = pending(f)
        val key = UUID.randomUUID()
        val response = overtimeBody(withdraw(f, id, 1, key))
        assertEquals(response, overtimeBody(withdraw(f, id, 1, key)))
        val view = details(f, id)
        assertEquals("WITHDRAWN", view["request"]["status"].asString())
        assertEquals("CANCELLED", view["approval"]["status"].asString())
        assertEquals(135, view["request"]["actual"]["workedMinutes"].asInt())
        overtimeBody(plan(f))
        assertEquals(3, rows(f, "overtime_requests"))
        assertEquals(0, rows(f, "approval_decisions"))
    }

    @Test
    fun staleEmploymentMissingPolicyAndInvalidScheduleRemainRecoverable() {
        val f = overtimeFixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        assertCode(
            plan(f, id, key, mapOf("expectedEmploymentVersion" to 10)),
            409,
            "stale_employment_version",
        )
        assertCode(
            plan(
                f,
                id,
                key,
                mapOf(
                    "workDate" to "2026-09-02",
                    "requested" to interval("2026-09-02T01:00:00Z", "2026-09-02T04:00:00Z"),
                ),
            ),
            422,
            "overtime_schedule_missing",
        )
        overtimeBody(plan(f, id, key))
        approvalPolicy(f, listOf(f.actor.accountId), 0, false)
        val submitted = UUID.randomUUID()
        assertCode(actual(f, id, submitted), 422, "approval_policy_missing")
        assertEquals("PLANNED", details(f, id)["request"]["status"].asString())
        approvalPolicy(f, listOf(f.actor.accountId), 1, true)
        overtimeBody(actual(f, id, submitted))
        overtimeBody(decide(f, id, decision = "REJECT"))
        assertEquals("REJECTED", details(f, id)["request"]["status"].asString())
        assertEquals(0, details(f, id)["request"]["approvedMinutes"].asInt())
    }
}
