package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.workforce.domain.entities.*
import java.time.LocalDate
import java.time.YearMonth

fun availableWorkPeriodActions(
    actor: Actor,
    month: YearMonth,
    state: WorkPeriodState?,
    companyDate: LocalDate,
): List<String> = buildList {
    val period = state?.period
    val attempt = state?.attempt
    if ("workforce.close" in actor.permissions) {
        if (month < YearMonth.from(companyDate) && (period == null || !workPeriodLocked(period)))
            add("close")
        if (
            period?.status == WorkPeriodStatus.PROCESSING &&
                attempt?.status in setOf(JobStatus.FAILED, JobStatus.CANCELLED)
        )
            add("recover")
    }
    if (
        attempt != null &&
            (attempt.initiatedBy == actor.accountId || "jobs.read" in actor.permissions)
    )
        add("view_job")
    if (period?.status == WorkPeriodStatus.CLOSED && "workforce.read" in actor.permissions)
        add("view_snapshots")
}
