package dev.fajar.hris.people.data.queries

import dev.fajar.hris.people.data.datasources.OnboardingScheduleRow
import dev.fajar.hris.schema.tables.BackgroundJobs.BACKGROUND_JOBS as J
import dev.fajar.hris.schema.tables.LifecycleTemplateRevisions.LIFECYCLE_TEMPLATE_REVISIONS as R
import dev.fajar.hris.schema.tables.LifecycleTemplates.LIFECYCLE_TEMPLATES as T
import dev.fajar.hris.schema.tables.OnboardingSchedules.ONBOARDING_SCHEDULES as S
import dev.fajar.hris.schema.tables.records.LifecycleTemplatesRecord
import java.time.OffsetDateTime
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record

/** Immutable checklist definitions and public job metadata are projected in one scoped snapshot. */
fun onboardingScheduleQuery(sql: DSLContext) =
    sql.select(
            S.fields().toList() +
                listOf(
                    T.CODE,
                    T.KIND,
                    R.NAME.`as`("retained_template_name"),
                    R.ACTIVE.`as`("retained_template_active"),
                    R.TASKS.`as`("retained_template_tasks"),
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
        .join(T)
        .on(T.COMPANY_ID.eq(S.COMPANY_ID), T.ID.eq(S.TEMPLATE_ID))
        .join(R)
        .on(
            R.COMPANY_ID.eq(S.COMPANY_ID),
            R.ID.eq(S.TEMPLATE_ID),
            R.REVISION.eq(S.TEMPLATE_VERSION),
        )
        .join(J)
        .on(J.COMPANY_ID.eq(S.COMPANY_ID), J.ID.eq(S.JOB_ID))

fun Record.toOnboardingScheduleRow(): OnboardingScheduleRow {
    val schedule = into(S)
    val template =
        LifecycleTemplatesRecord().also {
            it.companyId = schedule.companyId
            it.id = schedule.templateId
            it.version = schedule.templateVersion
            it.code = get(T.CODE)!!
            it.kind = get(T.KIND)!!
            it.name = get("retained_template_name", String::class.java)!!
            it.active = get("retained_template_active", Boolean::class.java)!!
            it.tasks = get("retained_template_tasks", JSONB::class.java)!!
        }
    return OnboardingScheduleRow(
        schedule,
        template,
        get("job_status", String::class.java)!!,
        get("job_version", Long::class.java)!!,
        get(J.ATTEMPTS)!!,
        get(J.CANCELLATION_REQUESTED)!!,
        get(J.COMPLETED_ITEMS)!!,
        get("job_failure_code", String::class.java),
        get("job_finished_at", OffsetDateTime::class.java),
    )
}
