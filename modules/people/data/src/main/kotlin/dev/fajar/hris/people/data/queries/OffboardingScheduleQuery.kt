package dev.fajar.hris.people.data.queries

import dev.fajar.hris.people.data.datasources.OffboardingScheduleRow
import dev.fajar.hris.schema.tables.BackgroundJobs.BACKGROUND_JOBS as J
import dev.fajar.hris.schema.tables.OffboardingSchedules.OFFBOARDING_SCHEDULES as S
import java.time.OffsetDateTime
import org.jooq.DSLContext
import org.jooq.Record

/** One snapshot projects the retained authorization and the job's public progress fields. */
fun offboardingScheduleQuery(sql: DSLContext) =
    sql.select(
            S.fields().toList() +
                listOf(
                    J.STATUS.`as`("job_status"),
                    J.VERSION.`as`("job_version"),
                    J.ATTEMPTS,
                    J.CANCELLATION_REQUESTED,
                    J.COMPLETED_ITEMS,
                    J.FAILURE_CODE.`as`("job_failure_code"),
                    J.FINISHED_AT.`as`("job_finished_at"),
                )
        )
        .from(S)
        .join(J)
        .on(J.ID.eq(S.JOB_ID), J.COMPANY_ID.eq(S.COMPANY_ID))

fun Record.toOffboardingScheduleRow() =
    OffboardingScheduleRow(
        into(S),
        get("job_status", String::class.java)!!,
        get("job_version", Long::class.java)!!,
        get(J.ATTEMPTS)!!,
        get(J.CANCELLATION_REQUESTED)!!,
        get(J.COMPLETED_ITEMS)!!,
        get("job_failure_code", String::class.java),
        get("job_finished_at", OffsetDateTime::class.java),
    )
