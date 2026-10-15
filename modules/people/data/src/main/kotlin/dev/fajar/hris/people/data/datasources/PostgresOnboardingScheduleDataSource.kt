package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.people.data.queries.*
import dev.fajar.hris.schema.tables.OnboardingSchedules.ONBOARDING_SCHEDULES as S
import dev.fajar.hris.schema.tables.records.OnboardingSchedulesRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresOnboardingScheduleDataSource(private val sql: DSLContext) :
    OnboardingScheduleDataSource {
    override fun forEmployee(companyId: UUID, employmentId: UUID, limit: Int) =
        onboardingScheduleQuery(sql)
            .where(S.COMPANY_ID.eq(companyId), S.EMPLOYMENT_ID.eq(employmentId))
            .orderBy(S.ATTEMPT)
            .limit(limit)
            .fetch { it.toOnboardingScheduleRow() }

    override fun find(companyId: UUID, id: UUID) =
        onboardingScheduleQuery(sql).where(S.COMPANY_ID.eq(companyId), S.ID.eq(id)).fetchOne {
            it.toOnboardingScheduleRow()
        }

    override fun forJob(companyId: UUID, jobId: UUID): OnboardingScheduleRow? =
        onboardingScheduleQuery(sql)
            .where(S.COMPANY_ID.eq(companyId), S.JOB_ID.eq(jobId))
            .fetchOne { it.toOnboardingScheduleRow() }

    override fun insert(record: OnboardingSchedulesRecord) {
        sql.insertInto(S).set(record).execute()
    }

    override fun finish(
        companyId: UUID,
        id: UUID,
        expectedVersion: Long,
        status: String,
        finishedAt: OffsetDateTime,
        failureCode: String?,
    ): Long? =
        sql.update(S)
            .set(S.STATUS, status)
            .set(S.FINISHED_AT, finishedAt)
            .set(S.FAILURE_CODE, failureCode)
            .set(S.VERSION, S.VERSION.plus(1))
            .where(
                S.COMPANY_ID.eq(companyId),
                S.ID.eq(id),
                S.VERSION.eq(expectedVersion),
                S.STATUS.eq("SCHEDULED"),
            )
            .returning(S.VERSION)
            .fetchOne(S.VERSION)
}
