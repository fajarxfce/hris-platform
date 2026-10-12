package dev.fajar.hris.workforce.domain

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.availableWorkPeriodActions
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkPeriodActionsTest {
    private val now = Instant.parse("2026-10-01T00:00:00Z")
    private val month = YearMonth.of(2026, 9)
    private val today = LocalDate.of(2026, 10, 1)
    private val actor =
        Actor(
            UUID.randomUUID(),
            UUID.randomUUID(),
            setOf("workforce.close"),
            now,
            UUID.randomUUID(),
        )
    private val job =
        WorkPeriodAttempt(
            UUID.randomUUID(),
            actor.accountId,
            JobStatus.RUNNING,
            10,
            3,
            false,
            null,
            now,
            null,
        )
    private val period =
        WorkPeriod(
            UUID.randomUUID(),
            month,
            WorkPeriodStatus.PROCESSING,
            "Asia/Jakarta",
            job.id,
            1,
            now,
            null,
            null,
        )

    @Test
    fun onlyEndedMutablePeriodsCanStartAndRecoveryNeedsAStoppedProcessingAttempt() {
        assertEquals(listOf("close"), availableWorkPeriodActions(actor, month, null, today))
        assertTrue(availableWorkPeriodActions(actor, month, null, today.minusDays(1)).isEmpty())
        val processing = WorkPeriodState(period, job)
        assertEquals(
            listOf("view_job"),
            availableWorkPeriodActions(actor, month, processing, today),
        )
        for (status in listOf(JobStatus.FAILED, JobStatus.CANCELLED)) {
            assertEquals(
                listOf("recover", "view_job"),
                availableWorkPeriodActions(
                    actor,
                    month,
                    processing.copy(attempt = job.copy(status = status)),
                    today,
                ),
            )
        }
        assertEquals(
            listOf("close", "view_job"),
            availableWorkPeriodActions(
                actor,
                month,
                processing.copy(period = period.copy(status = WorkPeriodStatus.REVIEW_REQUIRED)),
                today,
            ),
        )
    }

    @Test
    fun closingDoesNotGrantEmployeeSnapshotsOrAnotherOperatorsJob() {
        val closed =
            WorkPeriodState(
                period.copy(status = WorkPeriodStatus.CLOSED),
                job.copy(initiatedBy = UUID.randomUUID(), status = JobStatus.SUCCEEDED),
            )
        assertTrue(availableWorkPeriodActions(actor, month, closed, today).isEmpty())
        assertEquals(
            listOf("view_snapshots"),
            availableWorkPeriodActions(
                actor.copy(permissions = setOf("workforce.read")),
                month,
                closed,
                today,
            ),
        )
        assertEquals(
            listOf("view_job", "view_snapshots"),
            availableWorkPeriodActions(
                actor.copy(permissions = setOf("workforce.read", "jobs.read")),
                month,
                closed,
                today,
            ),
        )
    }
}
