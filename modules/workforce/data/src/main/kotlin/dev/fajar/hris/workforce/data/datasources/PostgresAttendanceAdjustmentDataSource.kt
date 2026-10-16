package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.Tables.ATTENDANCE_ADJUSTMENTS as A
import dev.fajar.hris.schema.tables.records.AttendanceAdjustmentsRecord
import dev.fajar.hris.workforce.data.models.AttendanceAdjustmentSummaryRow
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresAttendanceAdjustmentDataSource(private val sql: DSLContext) :
    AttendanceAdjustmentDataSource {
    override fun latest(
        companyId: UUID,
        jobId: UUID,
        employeeId: UUID,
    ): AttendanceAdjustmentsRecord? =
        sql.selectFrom(A)
            .where(A.COMPANY_ID.eq(companyId), A.JOB_ID.eq(jobId), A.EMPLOYMENT_ID.eq(employeeId))
            .orderBy(A.REVISION.desc())
            .limit(1)
            .fetchOne()

    override fun find(companyId: UUID, id: UUID): AttendanceAdjustmentsRecord? =
        sql.selectFrom(A).where(A.COMPANY_ID.eq(companyId), A.ID.eq(id)).fetchOne()

    override fun history(
        companyId: UUID,
        jobId: UUID,
        employeeId: UUID,
        before: Long?,
        limit: Int,
    ): List<AttendanceAdjustmentSummaryRow> =
        sql.select(
                A.ID,
                A.REVISION,
                A.WORK_DATE,
                A.CORRECTION_ID,
                A.ACTOR_ID,
                A.RECORDED_AT,
                A.REASON,
            )
            .from(A)
            .where(
                A.COMPANY_ID.eq(companyId),
                A.JOB_ID.eq(jobId),
                A.EMPLOYMENT_ID.eq(employeeId),
                before?.let { A.REVISION.lt(it) } ?: DSL.noCondition(),
            )
            .orderBy(A.REVISION.desc())
            .limit(limit)
            .fetch {
                AttendanceAdjustmentSummaryRow(
                    it.get(A.ID)!!,
                    it.get(A.REVISION)!!,
                    it.get(A.WORK_DATE)!!,
                    it.get(A.CORRECTION_ID)!!,
                    it.get(A.ACTOR_ID)!!,
                    it.get(A.RECORDED_AT)!!,
                    it.get(A.REASON)!!,
                )
            }

    override fun append(row: AttendanceAdjustmentsRecord) {
        check(sql.insertInto(A).set(row).execute() == 1)
    }
}
