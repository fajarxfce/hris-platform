package dev.fajar.hris.sync.data.datasources

import dev.fajar.hris.schema.tables.AttendanceSyncHeads.ATTENDANCE_SYNC_HEADS as A
import dev.fajar.hris.schema.tables.DirectoryEmployeeRecords.DIRECTORY_EMPLOYEE_RECORDS as D
import dev.fajar.hris.schema.tables.DirectoryRefreshQueue.DIRECTORY_REFRESH_QUEUE as Q
import dev.fajar.hris.schema.tables.Documents.DOCUMENTS as DC
import dev.fajar.hris.schema.tables.ExpenseClaims.EXPENSE_CLAIMS as E
import dev.fajar.hris.schema.tables.ExpensePaymentHeads.EXPENSE_PAYMENT_HEADS as EP
import dev.fajar.hris.schema.tables.InboxItems.INBOX_ITEMS as I
import dev.fajar.hris.schema.tables.LeaveAccounts.LEAVE_ACCOUNTS as B
import dev.fajar.hris.schema.tables.LeaveRequests.LEAVE_REQUESTS as L
import dev.fajar.hris.schema.tables.MobileSyncChanges.MOBILE_SYNC_CHANGES as C
import dev.fajar.hris.schema.tables.MobileSyncHeads.MOBILE_SYNC_HEADS as H
import dev.fajar.hris.schema.tables.OvertimeRequests.OVERTIME_REQUESTS as O
import dev.fajar.hris.schema.tables.PayrollAssessments.PAYROLL_ASSESSMENTS as P
import dev.fajar.hris.schema.tables.PayrollPaymentHeads.PAYROLL_PAYMENT_HEADS as S
import dev.fajar.hris.schema.tables.RosterDays.ROSTER_DAYS as R
import dev.fajar.hris.schema.tables.SelfProfileRecords.SELF_PROFILE_RECORDS as SP
import dev.fajar.hris.schema.tables.SelfProfileRefreshQueue.SELF_PROFILE_REFRESH_QUEUE as SQ
import dev.fajar.hris.schema.tables.WorkHolidays.WORK_HOLIDAYS as W
import dev.fajar.hris.schema.tables.WorkScheduleSyncHeads.WORK_SCHEDULE_SYNC_HEADS as T
import dev.fajar.hris.schema.tables.records.*
import dev.fajar.hris.sync.data.models.*
import dev.fajar.hris.sync.data.queries.syncAudience
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresSyncDataSource(private val sql: DSLContext) : SyncDataSource {
    override fun head(companyId: UUID): MobileSyncHeadsRecord =
        sql.selectFrom(H).where(H.COMPANY_ID.eq(companyId)).forShare().fetchSingle()

    override fun snapshot(
        selection: SyncSelection,
        afterCollection: String?,
        afterId: UUID?,
        limit: Int,
    ): List<SyncResourceRow> {
        val expenses =
            sql.select(
                    DSL.inline("EXPENSE_CLAIMS").`as`("collection"),
                    E.ID.`as`("resource_id"),
                    E.VERSION.`as`("resource_version"),
                )
                .from(E)
                .where(
                    E.COMPANY_ID.eq(selection.companyId),
                    E.EMPLOYMENT_ID.`in`(selection.employmentIds),
                    DSL.inline("EXPENSE_CLAIMS").`in`(selection.collections),
                )
        val expensePayments =
            sql.select(
                    DSL.inline("EXPENSE_PAYMENTS").`as`("collection"),
                    EP.CLAIM_ID.`as`("resource_id"),
                    EP.VERSION.`as`("resource_version"),
                )
                .from(EP)
                .where(
                    EP.COMPANY_ID.eq(selection.companyId),
                    EP.EMPLOYMENT_ID.`in`(selection.employmentIds),
                    DSL.inline("EXPENSE_PAYMENTS").`in`(selection.collections),
                )
        val leaves =
            sql.select(
                    DSL.inline("LEAVE_REQUESTS").`as`("collection"),
                    L.ID.`as`("resource_id"),
                    L.VERSION.`as`("resource_version"),
                )
                .from(L)
                .where(
                    L.COMPANY_ID.eq(selection.companyId),
                    L.EMPLOYMENT_ID.`in`(selection.employmentIds)
                        .or(L.OWNER_ACCOUNT_ID.eq(selection.accountId)),
                    DSL.inline("LEAVE_REQUESTS").`in`(selection.collections),
                )
        val overtime =
            sql.select(
                    DSL.inline("OVERTIME_REQUESTS").`as`("collection"),
                    O.ID.`as`("resource_id"),
                    O.VERSION.`as`("resource_version"),
                )
                .from(O)
                .where(
                    O.COMPANY_ID.eq(selection.companyId),
                    O.EMPLOYMENT_ID.`in`(selection.employmentIds),
                    DSL.inline("OVERTIME_REQUESTS").`in`(selection.collections),
                )
        val balances =
            sql.select(
                    DSL.inline("LEAVE_BALANCES").`as`("collection"),
                    B.ID.`as`("resource_id"),
                    B.VERSION.`as`("resource_version"),
                )
                .from(B)
                .where(
                    B.COMPANY_ID.eq(selection.companyId),
                    B.EMPLOYMENT_ID.`in`(selection.employmentIds),
                    B.VERSION.gt(0),
                    DSL.inline("LEAVE_BALANCES").`in`(selection.collections),
                )
        val payslips =
            sql.select(
                    DSL.inline("PAYSLIPS").`as`("collection"),
                    P.ID.`as`("resource_id"),
                    DSL.inline(0L).`as`("resource_version"),
                )
                .from(P)
                .where(
                    P.COMPANY_ID.eq(selection.companyId),
                    P.EMPLOYMENT_ID.`in`(selection.employmentIds),
                    DSL.inline("PAYSLIPS").`in`(selection.collections),
                )
        val payments =
            sql.select(
                    DSL.inline("PAYROLL_PAYMENTS").`as`("collection"),
                    S.ASSESSMENT_ID.`as`("resource_id"),
                    S.VERSION.`as`("resource_version"),
                )
                .from(S)
                .where(
                    S.COMPANY_ID.eq(selection.companyId),
                    S.EMPLOYMENT_ID.`in`(selection.employmentIds),
                    DSL.inline("PAYROLL_PAYMENTS").`in`(selection.collections),
                )
        val inbox =
            sql.select(
                    DSL.inline("INBOX").`as`("collection"),
                    I.ID.`as`("resource_id"),
                    I.VERSION.`as`("resource_version"),
                )
                .from(I)
                .where(
                    I.COMPANY_ID.eq(selection.companyId),
                    I.OWNER_ACCOUNT_ID.eq(selection.accountId),
                    I.WITHDRAWN.isFalse,
                    DSL.inline("INBOX").`in`(selection.collections),
                )
        val attendance =
            sql.select(
                    DSL.inline("ATTENDANCE_DAYS").`as`("collection"),
                    A.ID.`as`("resource_id"),
                    A.VERSION.`as`("resource_version"),
                )
                .from(A)
                .where(
                    A.COMPANY_ID.eq(selection.companyId),
                    A.EMPLOYMENT_ID.`in`(selection.employmentIds),
                    DSL.inline("ATTENDANCE_DAYS").`in`(selection.collections),
                )
        val schedules =
            sql.select(
                    DSL.inline("WORK_SCHEDULES").`as`("collection"),
                    T.ID.`as`("resource_id"),
                    T.REVISION.`as`("resource_version"),
                )
                .from(T)
                .where(
                    T.COMPANY_ID.eq(selection.companyId),
                    T.EMPLOYMENT_ID.`in`(selection.employmentIds),
                    DSL.inline("WORK_SCHEDULES").`in`(selection.collections),
                )
        val roster =
            sql.select(
                    DSL.inline("ROSTER_DAYS").`as`("collection"),
                    R.ID.`as`("resource_id"),
                    R.VERSION.`as`("resource_version"),
                )
                .from(R)
                .where(
                    R.COMPANY_ID.eq(selection.companyId),
                    R.EMPLOYMENT_ID.`in`(selection.employmentIds),
                    DSL.inline("ROSTER_DAYS").`in`(selection.collections),
                )
        val holidays =
            sql.select(
                    DSL.inline("WORK_HOLIDAYS").`as`("collection"),
                    W.ID.`as`("resource_id"),
                    W.VERSION.`as`("resource_version"),
                )
                .from(W)
                .where(
                    W.COMPANY_ID.eq(selection.companyId),
                    DSL.condition(selection.employmentIds.isNotEmpty()),
                    DSL.inline("WORK_HOLIDAYS").`in`(selection.collections),
                )
        val directory =
            sql.select(
                    DSL.inline("DIRECTORY_EMPLOYEES").`as`("collection"),
                    D.ID.`as`("resource_id"),
                    D.VERSION.`as`("resource_version"),
                )
                .from(D)
                .where(
                    D.COMPANY_ID.eq(selection.companyId),
                    D.NAME.isNotNull,
                    DSL.inline("DIRECTORY_EMPLOYEES").`in`(selection.collections),
                )
        val documents =
            sql.select(
                    DSL.inline("DOCUMENT_EVIDENCE").`as`("collection"),
                    DC.ID.`as`("resource_id"),
                    DC.VERSION.`as`("resource_version"),
                )
                .from(DC)
                .where(
                    DC.COMPANY_ID.eq(selection.companyId),
                    DC.EMPLOYMENT_ID.`in`(selection.employmentIds),
                    DC.CLASSIFICATION.ne("HR_ONLY"),
                    DC.CURRENT_REVISION_ID.isNotNull,
                    DSL.inline("DOCUMENT_EVIDENCE").`in`(selection.collections),
                )
        val profiles =
            sql.select(
                    DSL.inline("SELF_PROFILES").`as`("collection"),
                    SP.ID.`as`("resource_id"),
                    SP.VERSION.`as`("resource_version"),
                )
                .from(SP)
                .where(
                    SP.COMPANY_ID.eq(selection.companyId),
                    SP.ID.`in`(selection.employmentIds),
                    SP.ACCOUNT_ID.eq(selection.accountId),
                    SP.PAYLOAD.isNotNull,
                    DSL.inline("SELF_PROFILES").`in`(selection.collections),
                )
        val records =
            expenses
                .unionAll(expensePayments)
                .unionAll(leaves)
                .unionAll(overtime)
                .unionAll(balances)
                .unionAll(payslips)
                .unionAll(payments)
                .unionAll(inbox)
                .unionAll(attendance)
                .unionAll(schedules)
                .unionAll(roster)
                .unionAll(holidays)
                .unionAll(directory)
                .unionAll(documents)
                .unionAll(profiles)
                .asTable("sync_resources")
        val collection = requireNotNull(records.field("collection", String::class.java))
        val id = requireNotNull(records.field("resource_id", UUID::class.java))
        val version = requireNotNull(records.field("resource_version", Long::class.java))
        return sql.select(collection, id, version)
            .from(records)
            .where(
                if (afterCollection == null || afterId == null) DSL.noCondition()
                else DSL.row(collection, id).gt(DSL.row(afterCollection, afterId))
            )
            .orderBy(collection, id)
            .limit(limit)
            .fetch {
                SyncResourceRow(
                    requireNotNull(it[collection]),
                    requireNotNull(it[id]),
                    requireNotNull(it[version]),
                )
            }
    }

    override fun changes(
        selection: SyncSelection,
        after: Long,
        upper: Long,
        limit: Int,
    ): List<MobileSyncChangesRecord> =
        sql.selectFrom(C)
            .where(syncAudience(selection), C.SEQUENCE.gt(after), C.SEQUENCE.le(upper))
            .orderBy(C.SEQUENCE)
            .limit(limit)
            .fetch()

    override fun pending(selection: SyncSelection, now: java.time.Instant): Boolean =
        sql.fetchValue(
            DSL.select(
                DSL.exists(
                        DSL.selectOne().from(C).where(syncAudience(selection), C.SEQUENCE.isNull)
                    )
                    .or(
                        DSL.exists(
                            DSL.selectOne()
                                .from(Q)
                                .where(
                                    Q.COMPANY_ID.eq(selection.companyId),
                                    Q.REFRESH_AT.le(now.atOffset(java.time.ZoneOffset.UTC)),
                                    DSL.inline("DIRECTORY_EMPLOYEES").`in`(selection.collections),
                                )
                        )
                    )
                    .or(
                        DSL.exists(
                            DSL.selectOne()
                                .from(SQ)
                                .where(
                                    SQ.COMPANY_ID.eq(selection.companyId),
                                    SQ.EMPLOYMENT_ID.`in`(selection.employmentIds),
                                    SQ.REFRESH_AT.le(now.atOffset(java.time.ZoneOffset.UTC)),
                                    DSL.inline("SELF_PROFILES").`in`(selection.collections),
                                )
                        )
                    )
            )
        ) == true

    override fun publish(limit: Int): Int =
        requireNotNull(
            sql.fetchOne("select publish_mobile_sync_changes(?)", limit)?.get(0, Int::class.java)
        )

    override fun prune(limit: Int): Int =
        requireNotNull(
            sql.fetchOne("select prune_mobile_sync_changes(?)", limit)?.get(0, Int::class.java)
        )
}
