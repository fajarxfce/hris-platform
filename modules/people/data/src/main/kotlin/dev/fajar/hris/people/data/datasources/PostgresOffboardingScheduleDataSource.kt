package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.people.data.queries.*
import dev.fajar.hris.schema.tables.OffboardingSchedules.OFFBOARDING_SCHEDULES as S
import dev.fajar.hris.schema.tables.records.OffboardingSchedulesRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresOffboardingScheduleDataSource(private val sql: DSLContext) :
    OffboardingScheduleDataSource {
    override fun forCase(companyId: UUID, caseId: UUID, limit: Int) =
        offboardingScheduleQuery(sql)
            .where(S.COMPANY_ID.eq(companyId), S.CASE_ID.eq(caseId))
            .orderBy(S.ATTEMPT)
            .limit(limit)
            .fetch { it.toOffboardingScheduleRow() }

    override fun find(companyId: UUID, id: UUID) =
        offboardingScheduleQuery(sql).where(S.COMPANY_ID.eq(companyId), S.ID.eq(id)).fetchOne {
            it.toOffboardingScheduleRow()
        }

    override fun forJob(companyId: UUID, jobId: UUID): OffboardingSchedulesRecord? =
        sql.selectFrom(S).where(S.COMPANY_ID.eq(companyId), S.JOB_ID.eq(jobId)).fetchOne()

    override fun insert(record: OffboardingSchedulesRecord) {
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
