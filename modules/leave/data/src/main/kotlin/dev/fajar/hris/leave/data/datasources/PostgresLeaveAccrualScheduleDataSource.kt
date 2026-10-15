package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveAccrualSchedulePeriodRow
import dev.fajar.hris.schema.tables.BackgroundJobs.BACKGROUND_JOBS as J
import dev.fajar.hris.schema.tables.LeaveAccrualScheduleEntries.LEAVE_ACCRUAL_SCHEDULE_ENTRIES as E
import dev.fajar.hris.schema.tables.LeaveAccrualSchedules.LEAVE_ACCRUAL_SCHEDULES as S
import dev.fajar.hris.schema.tables.LeaveBatchResults.LEAVE_BATCH_RESULTS as R
import dev.fajar.hris.schema.tables.LeaveBatches.LEAVE_BATCHES as B
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresLeaveAccrualScheduleDataSource(private val sql: DSLContext) :
    LeaveAccrualScheduleDataSource {
    override fun insert(
        schedule: LeaveAccrualSchedulesRecord,
        entries: List<LeaveAccrualScheduleEntriesRecord>,
    ) {
        sql.insertInto(S).set(schedule).execute()
        sql.batchInsert(entries).execute()
    }

    override fun find(company: UUID, id: UUID): LeaveAccrualSchedulesRecord? =
        sql.selectFrom(S).where(S.COMPANY_ID.eq(company), S.ID.eq(id)).fetchOne()

    override fun list(company: UUID, after: UUID?, limit: Int): List<LeaveAccrualSchedulesRecord> =
        sql.selectFrom(S)
            .where(S.COMPANY_ID.eq(company))
            .and(after?.let { S.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(S.ID)
            .limit(limit)
            .fetch()

    override fun periods(company: UUID, id: UUID): List<LeaveAccrualSchedulePeriodRow> {
        val completed =
            DSL.field(
                    sql.selectCount()
                        .from(R)
                        .where(R.COMPANY_ID.eq(E.COMPANY_ID), R.BATCH_ID.eq(E.BATCH_ID))
                )
                .`as`("completed_employees")
        return sql.select(*B.fields())
            .select(E.SCHEDULED_FOR, J.STATUS, completed)
            .from(E)
            .join(B)
            .on(B.COMPANY_ID.eq(E.COMPANY_ID), B.ID.eq(E.BATCH_ID))
            .join(J)
            .on(J.COMPANY_ID.eq(B.COMPANY_ID), J.ID.eq(B.JOB_ID))
            .where(E.COMPANY_ID.eq(company), E.SCHEDULE_ID.eq(id))
            .orderBy(E.ORDINAL)
            .limit(12)
            .fetch {
                LeaveAccrualSchedulePeriodRow(
                    requireNotNull(it[E.SCHEDULED_FOR]),
                    it.into(B),
                    requireNotNull(it[J.STATUS]),
                    requireNotNull(it[completed]),
                )
            }
    }
}
