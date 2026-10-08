package dev.fajar.hris.approvals.data.datasources

import dev.fajar.hris.schema.tables.ApprovalTemplateRevisions.APPROVAL_TEMPLATE_REVISIONS as R
import dev.fajar.hris.schema.tables.ApprovalTemplates.APPROVAL_TEMPLATES as T
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext

class PostgresApprovalPolicyDataSource(private val sql: DSLContext) : ApprovalPolicyDataSource {
    override fun list(companyId: UUID, kind: String, asOf: LocalDate): List<TemplateRow> {
        val latest = R.`as`("latest")
        return sql.select(T.asterisk(), R.asterisk())
            .from(T)
            .join(R)
            .on(R.COMPANY_ID.eq(T.COMPANY_ID))
            .and(R.TEMPLATE_ID.eq(T.ID))
            .where(T.COMPANY_ID.eq(companyId))
            .and(T.KIND.eq(kind))
            .and(
                R.REVISION.eq(
                    sql.select(latest.REVISION)
                        .from(latest)
                        .where(latest.COMPANY_ID.eq(T.COMPANY_ID))
                        .and(latest.TEMPLATE_ID.eq(T.ID))
                        .and(latest.EFFECTIVE_FROM.le(asOf))
                        .orderBy(latest.EFFECTIVE_FROM.desc(), latest.REVISION.desc())
                        .limit(1)
                )
            )
            .orderBy(T.ID)
            .fetch { TemplateRow(it.into(T), it.into(R)) }
    }

    override fun find(companyId: UUID, id: UUID): TemplateRow? =
        sql.select(T.asterisk(), R.asterisk())
            .from(T)
            .join(R)
            .on(R.COMPANY_ID.eq(T.COMPANY_ID))
            .and(R.TEMPLATE_ID.eq(T.ID))
            .where(T.COMPANY_ID.eq(companyId))
            .and(T.ID.eq(id))
            .orderBy(R.REVISION.desc())
            .limit(1)
            .fetchOne { TemplateRow(it.into(T), it.into(R)) }

    override fun insert(row: ApprovalTemplatesRecord) {
        sql.insertInto(T).set(row).execute()
    }

    override fun update(row: ApprovalTemplatesRecord, expectedVersion: Long): Long? =
        sql.update(T)
            .set(T.NAME, row.name)
            .set(T.ACTIVE, row.active)
            .set(T.VERSION, expectedVersion + 1)
            .where(T.COMPANY_ID.eq(row.companyId))
            .and(T.ID.eq(row.id))
            .and(T.VERSION.eq(expectedVersion))
            .returning(T.VERSION)
            .fetchOne()
            ?.version

    override fun appendRevision(row: ApprovalTemplateRevisionsRecord) {
        sql.insertInto(R).set(row).execute()
    }
}
