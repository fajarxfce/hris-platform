package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.Tables.EMPLOYMENTS as M
import dev.fajar.hris.schema.Tables.LIFECYCLE_CASES as C
import dev.fajar.hris.schema.Tables.LIFECYCLE_EVENTS as E
import dev.fajar.hris.schema.Tables.LIFECYCLE_TASKS as K
import dev.fajar.hris.schema.Tables.LIFECYCLE_TEMPLATES as T
import dev.fajar.hris.schema.Tables.LIFECYCLE_TEMPLATE_REVISIONS as R
import dev.fajar.hris.schema.Tables.PERSONS as P
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresLifecycleDataSource(private val sql: DSLContext) : LifecycleDataSource {
    override fun lockTemplates(companyId: UUID, shared: Boolean) {
        sql.query(
                if (shared) "select pg_advisory_xact_lock_shared(hashtextextended(?,0))"
                else "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "lifecycle-templates:$companyId",
            )
            .execute()
    }

    override fun templateCount(companyId: UUID) =
        sql.fetchCount(sql.selectOne().from(T).where(T.COMPANY_ID.eq(companyId)))

    override fun template(companyId: UUID, id: UUID) =
        sql.selectFrom(T).where(T.COMPANY_ID.eq(companyId)).and(T.ID.eq(id)).fetchOne()

    override fun templates(companyId: UUID, after: String?, limit: Int) =
        sql.selectFrom(T)
            .where(T.COMPANY_ID.eq(companyId))
            .and(after?.let { T.CODE.gt(it) } ?: DSL.noCondition())
            .orderBy(T.CODE)
            .limit(limit)
            .fetch()

    override fun insertTemplate(record: LifecycleTemplatesRecord) {
        sql.insertInto(T).set(record).execute()
    }

    override fun updateTemplate(record: LifecycleTemplatesRecord, expectedVersion: Long): Long? =
        sql.update(T)
            .set(T.NAME, record.name)
            .set(T.ACTIVE, record.active)
            .set(T.TASKS, record.tasks)
            .set(T.VERSION, expectedVersion + 1)
            .where(T.COMPANY_ID.eq(record.companyId))
            .and(T.ID.eq(record.id))
            .and(T.VERSION.eq(expectedVersion))
            .returning(T.VERSION)
            .fetchOne()
            ?.version

    override fun insertTemplateRevision(record: LifecycleTemplateRevisionsRecord) {
        sql.insertInto(R).set(record).execute()
    }

    override fun lockCase(companyId: UUID, id: UUID, shared: Boolean) {
        val query = sql.select(C.ID).from(C).where(C.COMPANY_ID.eq(companyId)).and(C.ID.eq(id))
        if (shared) query.forShare().fetch() else query.forUpdate().fetch()
    }

    override fun case(companyId: UUID, id: UUID) =
        sql.selectFrom(C).where(C.COMPANY_ID.eq(companyId)).and(C.ID.eq(id)).fetchOne()

    override fun caseDetails(companyId: UUID, id: UUID) =
        sql.select(
                C,
                DSL.multiset(
                    DSL.selectFrom(K)
                        .where(K.COMPANY_ID.eq(companyId))
                        .and(K.CASE_ID.eq(C.ID))
                        .orderBy(K.KEY)
                        .limit(64)
                ),
                M.EMPLOYEE_NUMBER,
                P.LEGAL_NAME,
            )
            .from(C)
            .join(M)
            .on(M.COMPANY_ID.eq(C.COMPANY_ID).and(M.ID.eq(C.EMPLOYMENT_ID)))
            .join(P)
            .on(P.ID.eq(M.PERSON_ID))
            .where(C.COMPANY_ID.eq(companyId))
            .and(C.ID.eq(id))
            .fetchOne {
                LifecycleCaseRow(it.value1(), it.value2().toList(), it.value3(), it.value4())
            }

    override fun cases(
        companyId: UUID,
        employeeId: UUID?,
        status: String?,
        after: UUID?,
        limit: Int,
    ) =
        sql.select(
                C,
                DSL.multiset(
                    DSL.selectFrom(K)
                        .where(K.COMPANY_ID.eq(companyId))
                        .and(K.CASE_ID.eq(C.ID))
                        .orderBy(K.KEY)
                        .limit(64)
                ),
                M.EMPLOYEE_NUMBER,
                P.LEGAL_NAME,
            )
            .from(C)
            .join(M)
            .on(M.COMPANY_ID.eq(C.COMPANY_ID).and(M.ID.eq(C.EMPLOYMENT_ID)))
            .join(P)
            .on(P.ID.eq(M.PERSON_ID))
            .where(C.COMPANY_ID.eq(companyId))
            .and(employeeId?.let { C.EMPLOYMENT_ID.eq(it) } ?: DSL.noCondition())
            .and(status?.let { C.STATUS.eq(it) } ?: DSL.noCondition())
            .and(after?.let { C.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(C.ID)
            .limit(limit)
            .fetch { LifecycleCaseRow(it.value1(), it.value2().toList(), it.value3(), it.value4()) }

    override fun completedOffboardingDate(companyId: UUID, employeeId: UUID) =
        sql.select(C.TARGET_DATE)
            .from(C)
            .where(C.COMPANY_ID.eq(companyId))
            .and(C.EMPLOYMENT_ID.eq(employeeId))
            .and(C.KIND.eq("OFFBOARDING"))
            .and(C.STATUS.eq("COMPLETED"))
            .fetchOne(C.TARGET_DATE)

    override fun hasOpenCases(companyId: UUID, employeeId: UUID, exceptId: UUID?) =
        sql.fetchExists(
            sql.selectOne()
                .from(C)
                .where(C.COMPANY_ID.eq(companyId))
                .and(C.EMPLOYMENT_ID.eq(employeeId))
                .and(C.STATUS.eq("OPEN"))
                .and(exceptId?.let { C.ID.ne(it) } ?: DSL.noCondition())
        )

    override fun tasks(companyId: UUID, ids: Set<UUID>) =
        sql.selectFrom(K)
            .where(K.COMPANY_ID.eq(companyId))
            .and(K.CASE_ID.`in`(ids))
            .orderBy(K.CASE_ID, K.KEY)
            .limit(ids.size * 64)
            .fetch()

    override fun insertCase(record: LifecycleCasesRecord) {
        sql.insertInto(C).set(record).execute()
    }

    override fun insertTasks(records: List<LifecycleTasksRecord>) {
        sql.batch(records.map { sql.insertInto(K).set(it) }).execute()
    }

    override fun advanceCase(
        companyId: UUID,
        id: UUID,
        expectedVersion: Long,
        status: String,
    ): Long? =
        sql.update(C)
            .set(C.VERSION, expectedVersion + 1)
            .set(C.STATUS, status)
            .where(C.COMPANY_ID.eq(companyId))
            .and(C.ID.eq(id))
            .and(C.VERSION.eq(expectedVersion))
            .and(C.STATUS.eq("OPEN"))
            .returning(C.VERSION)
            .fetchOne()
            ?.version

    override fun updateTask(record: LifecycleTasksRecord) =
        sql.update(K)
            .set(K.ASSIGNEE_ID, record.assigneeId)
            .set(K.STATUS, record.status)
            .set(K.COMPLETED_BY, record.completedBy)
            .set(K.COMPLETED_AT, record.completedAt)
            .where(K.COMPANY_ID.eq(record.companyId))
            .and(K.CASE_ID.eq(record.caseId))
            .and(K.KEY.eq(record.key))
            .execute()

    override fun insertEvent(record: LifecycleEventsRecord) {
        sql.insertInto(E).set(record).execute()
    }

    override fun history(companyId: UUID, id: UUID, after: Long?, limit: Int) =
        sql.selectFrom(E)
            .where(E.COMPANY_ID.eq(companyId))
            .and(E.CASE_ID.eq(id))
            .and(after?.let { E.VERSION.gt(it) } ?: DSL.noCondition())
            .orderBy(E.VERSION)
            .limit(limit)
            .fetch()

    override fun assigned(
        companyId: UUID,
        accountId: UUID,
        afterCase: UUID?,
        afterKey: String?,
        limit: Int,
    ) =
        sql.select(C, K, M.EMPLOYEE_NUMBER, P.LEGAL_NAME)
            .from(K)
            .join(C)
            .on(C.COMPANY_ID.eq(K.COMPANY_ID).and(C.ID.eq(K.CASE_ID)))
            .join(M)
            .on(M.COMPANY_ID.eq(C.COMPANY_ID).and(M.ID.eq(C.EMPLOYMENT_ID)))
            .join(P)
            .on(P.ID.eq(M.PERSON_ID))
            .where(K.COMPANY_ID.eq(companyId))
            .and(K.ASSIGNEE_ID.eq(accountId))
            .and(K.STATUS.eq("PENDING"))
            .and(C.STATUS.eq("OPEN"))
            .and(
                if (afterCase == null) DSL.noCondition()
                else K.CASE_ID.gt(afterCase).or(K.CASE_ID.eq(afterCase).and(K.KEY.gt(afterKey)))
            )
            .orderBy(K.CASE_ID, K.KEY)
            .limit(limit)
            .fetch { AssignedLifecycleRow(it.value1(), it.value2(), it.value3(), it.value4()) }
}
