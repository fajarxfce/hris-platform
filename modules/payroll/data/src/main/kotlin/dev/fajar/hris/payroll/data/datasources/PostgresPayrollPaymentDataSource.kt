package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.schema.tables.PayrollPayableCandidates.PAYROLL_PAYABLE_CANDIDATES as C
import dev.fajar.hris.schema.tables.PayrollPaymentActions.PAYROLL_PAYMENT_ACTIONS as A
import dev.fajar.hris.schema.tables.PayrollPaymentBatches.PAYROLL_PAYMENT_BATCHES as B
import dev.fajar.hris.schema.tables.PayrollPaymentItems.PAYROLL_PAYMENT_ITEMS as I
import dev.fajar.hris.schema.tables.PayrollPaymentResults.PAYROLL_PAYMENT_RESULTS as R
import dev.fajar.hris.schema.tables.records.*
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPayrollPaymentDataSource(private val sql: DSLContext) : PayrollPaymentDataSource {
    override fun progress(company: UUID, assessment: UUID): List<PayrollPaymentProgressRow> =
        sql.select(B.ID, I.ID, I.STATUS, B.CREATED_AT, B.RELEASED_AT, I.OCCURRED_AT)
            .from(I)
            .join(B)
            .on(B.COMPANY_ID.eq(I.COMPANY_ID).and(B.ID.eq(I.BATCH_ID)))
            .where(I.COMPANY_ID.eq(company))
            .and(I.ASSESSMENT_ID.eq(assessment))
            .orderBy(B.CREATED_AT.desc(), I.ID.desc())
            .limit(20)
            .fetch {
                PayrollPaymentProgressRow(
                    requireNotNull(it[B.ID]),
                    requireNotNull(it[I.ID]),
                    requireNotNull(it[I.STATUS]),
                    requireNotNull(it[B.CREATED_AT]),
                    it[B.RELEASED_AT],
                    it[I.OCCURRED_AT],
                )
            }

    override fun progressHead(
        company: UUID,
        assessment: UUID,
        allowedEmployees: Set<UUID>?,
    ): PayrollPaymentHeadRow? =
        sql.fetchOne(
                """
                SELECT a.id,coalesce(h.version,0) AS version FROM payroll_assessments a
                LEFT JOIN payroll_payment_heads h ON h.company_id=a.company_id AND h.assessment_id=a.id
                WHERE a.company_id=? AND a.id=? AND (?::uuid[] IS NULL OR a.employment_id=ANY(?::uuid[]))
                """
                    .trimIndent(),
                company,
                assessment,
                allowedEmployees?.toTypedArray(),
                allowedEmployees?.toTypedArray(),
            )
            ?.let {
                PayrollPaymentHeadRow(
                    it.get("id", UUID::class.java)!!,
                    it.get("version", Long::class.javaObjectType)!!,
                )
            }

    override fun lock(company: UUID, shared: Boolean) {
        val function = if (shared) "pg_advisory_xact_lock_shared" else "pg_advisory_xact_lock"
        sql.query("select $function(hashtextextended(?,0))", "payroll-payments:$company").execute()
    }

    override fun capacity(company: UUID): PayrollPaymentCapacityRow =
        sql.select(DSL.count(), DSL.count().filterWhere(B.STATUS.`in`("PREPARED", "RELEASED")))
            .from(B)
            .where(B.COMPANY_ID.eq(company))
            .fetchSingle { PayrollPaymentCapacityRow(it.value1(), it.value2()) }

    override fun candidates(
        company: UUID,
        assessments: Set<UUID>,
    ): List<PayrollPayableCandidatesRecord> =
        sql.selectFrom(C)
            .where(C.COMPANY_ID.eq(company))
            .and(C.ASSESSMENT_ID.`in`(assessments))
            .orderBy(C.ASSESSMENT_ID)
            .limit(101)
            .fetch()

    override fun occupiedAssessments(company: UUID, assessments: Set<UUID>): Set<UUID> =
        sql.select(I.ASSESSMENT_ID)
            .from(I)
            .where(I.COMPANY_ID.eq(company))
            .and(I.ASSESSMENT_ID.`in`(assessments))
            .and(I.STATUS.`in`("PREPARED", "PENDING", "SUCCEEDED"))
            .fetch(I.ASSESSMENT_ID)
            .filterNotNull()
            .toSet()

    override fun attempts(company: UUID, assessments: Set<UUID>): Map<UUID, Int> =
        sql.select(I.ASSESSMENT_ID, DSL.count())
            .from(I)
            .where(I.COMPANY_ID.eq(company))
            .and(I.ASSESSMENT_ID.`in`(assessments))
            .groupBy(I.ASSESSMENT_ID)
            .fetch()
            .associate { requireNotNull(it.value1()) to it.value2() }

    override fun payables(
        company: UUID,
        from: OffsetDateTime,
        until: OffsetDateTime,
        after: UUID?,
        limit: Int,
    ): List<PayrollPayableCandidatesRecord> {
        val cursor = C.`as`("cursor")
        val available =
            DSL.notExists(
                DSL.selectOne()
                    .from(I)
                    .where(I.COMPANY_ID.eq(C.COMPANY_ID))
                    .and(I.ASSESSMENT_ID.eq(C.ASSESSMENT_ID))
                    .and(I.STATUS.`in`("PREPARED", "PENDING", "SUCCEEDED"))
            )
        val attempts =
            DSL.selectCount()
                .from(I)
                .where(I.COMPANY_ID.eq(C.COMPANY_ID))
                .and(I.ASSESSMENT_ID.eq(C.ASSESSMENT_ID))
                .asField<Int>()
        val before =
            if (after == null) DSL.noCondition()
            else
                DSL.row(C.PUBLISHED_AT, C.ASSESSMENT_ID)
                    .lt(
                        DSL.select(cursor.PUBLISHED_AT, cursor.ASSESSMENT_ID)
                            .from(cursor)
                            .where(cursor.COMPANY_ID.eq(company))
                            .and(cursor.ASSESSMENT_ID.eq(after))
                            .and(cursor.PUBLISHED_AT.ge(from))
                            .and(cursor.PUBLISHED_AT.lt(until))
                    )
        return sql.selectFrom(C)
            .where(C.COMPANY_ID.eq(company))
            .and(C.PUBLISHED_AT.ge(from))
            .and(C.PUBLISHED_AT.lt(until))
            .and(available)
            .and(attempts.lt(20))
            .and(before)
            .orderBy(C.PUBLISHED_AT.desc(), C.ASSESSMENT_ID.desc())
            .limit(limit)
            .fetch()
    }

    override fun find(company: UUID, id: UUID): PayrollPaymentBatchesRecord? =
        sql.selectFrom(B).where(B.COMPANY_ID.eq(company)).and(B.ID.eq(id)).fetchOne()

    override fun items(company: UUID, id: UUID): List<PayrollPaymentItemsRecord> =
        sql.selectFrom(I)
            .where(I.COMPANY_ID.eq(company))
            .and(I.BATCH_ID.eq(id))
            .orderBy(I.ID)
            .limit(100)
            .fetch()

    override fun list(
        company: UUID,
        from: OffsetDateTime,
        until: OffsetDateTime,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<PayrollPaymentSummaryRow> {
        val cursor = B.`as`("cursor")
        val before =
            if (after == null) DSL.noCondition()
            else
                DSL.row(B.CREATED_AT, B.ID)
                    .lt(
                        DSL.select(cursor.CREATED_AT, cursor.ID)
                            .from(cursor)
                            .where(cursor.COMPANY_ID.eq(company))
                            .and(cursor.ID.eq(after))
                            .and(cursor.CREATED_AT.ge(from))
                            .and(cursor.CREATED_AT.lt(until))
                            .and(status?.let { cursor.STATUS.eq(it) } ?: DSL.noCondition())
                    )
        val pending = DSL.count().filterWhere(I.STATUS.eq("PENDING"))
        val succeeded = DSL.count().filterWhere(I.STATUS.eq("SUCCEEDED"))
        val failed = DSL.count().filterWhere(I.STATUS.eq("FAILED"))
        return sql.select(B.asterisk(), pending, succeeded, failed)
            .from(B)
            .join(I)
            .on(I.COMPANY_ID.eq(B.COMPANY_ID).and(I.BATCH_ID.eq(B.ID)))
            .where(B.COMPANY_ID.eq(company))
            .and(B.CREATED_AT.ge(from))
            .and(B.CREATED_AT.lt(until))
            .and(status?.let { B.STATUS.eq(it) } ?: DSL.noCondition())
            .and(before)
            .groupBy(*B.fields())
            .orderBy(B.CREATED_AT.desc(), B.ID.desc())
            .limit(limit)
            .fetch {
                PayrollPaymentSummaryRow(
                    it.into(B),
                    requireNotNull(it[pending]),
                    requireNotNull(it[succeeded]),
                    requireNotNull(it[failed]),
                )
            }
    }

    override fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<PayrollPaymentActionsRecord> =
        sql.selectFrom(A)
            .where(A.COMPANY_ID.eq(company))
            .and(A.BATCH_ID.eq(id))
            .and(after?.let { A.VERSION.gt(it) } ?: DSL.noCondition())
            .orderBy(A.VERSION)
            .limit(limit)
            .fetch()

    override fun results(company: UUID, id: UUID): List<PayrollPaymentResultsRecord> =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(company))
            .and(R.BATCH_ID.eq(id))
            .orderBy(R.BATCH_VERSION, R.ITEM_ID)
            .limit(100)
            .fetch()

    override fun insert(row: PayrollPaymentBatchesRecord) {
        sql.insertInto(B).set(row).execute()
    }

    override fun insertItems(rows: List<PayrollPaymentItemsRecord>) {
        sql.batchInsert(rows).execute()
    }

    override fun action(row: PayrollPaymentActionsRecord) {
        sql.insertInto(A).set(row).execute()
    }

    override fun advance(
        company: UUID,
        id: UUID,
        version: Long,
        status: String,
        releasedBy: UUID?,
        releasedAt: OffsetDateTime?,
    ): Long? =
        sql.update(B)
            .set(B.VERSION, version + 1)
            .set(B.STATUS, status)
            .set(B.RELEASED_BY, releasedBy)
            .set(B.RELEASED_AT, releasedAt)
            .where(B.COMPANY_ID.eq(company))
            .and(B.ID.eq(id))
            .and(B.VERSION.eq(version))
            .returning(B.VERSION)
            .fetchOne()
            ?.version

    override fun transitionItems(company: UUID, id: UUID, batchVersion: Long, status: String): Int =
        sql.update(I)
            .set(I.STATUS, status)
            .set(I.VERSION, 1)
            .set(I.BATCH_VERSION, batchVersion)
            .where(I.COMPANY_ID.eq(company))
            .and(I.BATCH_ID.eq(id))
            .and(I.STATUS.eq("PREPARED"))
            .execute()

    override fun reconcileItem(
        company: UUID,
        batch: UUID,
        id: UUID,
        batchVersion: Long,
        status: String,
        reference: String?,
        occurredAt: OffsetDateTime,
    ): Int =
        sql.update(I)
            .set(I.STATUS, status)
            .set(I.VERSION, 2)
            .set(I.BATCH_VERSION, batchVersion)
            .set(I.TRANSACTION_REFERENCE, reference)
            .set(I.OCCURRED_AT, occurredAt)
            .where(I.COMPANY_ID.eq(company))
            .and(I.BATCH_ID.eq(batch))
            .and(I.ID.eq(id))
            .and(I.STATUS.eq("PENDING"))
            .and(I.VERSION.eq(1))
            .execute()

    override fun recordResults(rows: List<PayrollPaymentResultsRecord>) {
        sql.batchInsert(rows).execute()
    }
}
