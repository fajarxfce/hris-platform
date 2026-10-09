package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.models.*
import dev.fajar.hris.schema.Tables.ACCOUNTS as A
import dev.fajar.hris.schema.Tables.AUDIENCE_GROUP_HEADS as H
import dev.fajar.hris.schema.Tables.AUDIENCE_GROUP_REVISIONS as R
import dev.fajar.hris.schema.Tables.COMPANY_MEMBERSHIPS as M
import dev.fajar.hris.schema.Tables.MEMBERSHIP_PERMISSIONS as P
import dev.fajar.hris.schema.tables.EmployeesAt.EMPLOYEES_AT
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresAnnouncementAudienceDataSource(private val sql: DSLContext) :
    AnnouncementAudienceDataSource {
    override fun recipients(
        query: AnnouncementRecipientQuery,
        limit: Int,
    ): List<AnnouncementRecipientRow> {
        val e = EMPLOYEES_AT.call(query.companyId, query.date)
        val audience =
            when (query.audienceKind) {
                "COMPANY" -> DSL.noCondition()
                "BRANCH" -> e.BRANCH_ID.`in`(query.targetIds)
                "DEPARTMENT" -> e.DEPARTMENT_ID.`in`(query.targetIds)
                "GROUP" ->
                    e.ID.`in`(
                        sql.select(
                                DSL.field(
                                    "{0}::uuid",
                                    UUID::class.java,
                                    DSL.field(DSL.name("member", "value"), String::class.java),
                                )
                            )
                            .from(H)
                            .join(R)
                            .on(
                                H.COMPANY_ID.eq(R.COMPANY_ID),
                                H.ID.eq(R.ID),
                                H.VERSION.eq(R.VERSION),
                            )
                            .crossJoin(
                                DSL.table("jsonb_array_elements_text({0})", R.MEMBER_IDS)
                                    .`as`("member", "value")
                            )
                            .where(H.COMPANY_ID.eq(query.companyId), H.ID.`in`(query.targetIds))
                    )
                else -> throw IllegalArgumentException("Unknown audience query")
            }
        return sql.select(e.ACCOUNT_ID, e.ID)
            .distinctOn(e.ACCOUNT_ID)
            .from(e)
            .join(A)
            .on(A.ID.eq(e.ACCOUNT_ID))
            .join(M)
            .on(M.COMPANY_ID.eq(query.companyId), M.ACCOUNT_ID.eq(A.ID))
            .where(
                audience,
                e.STATUS.`in`(query.employmentStatuses),
                e.START_DATE.le(query.date),
                e.END_DATE.isNull.or(e.END_DATE.ge(query.date)),
                A.ACTIVE.eq(query.accountActive),
                M.ACTIVE.eq(query.membershipActive),
                DSL.exists(
                    sql.selectOne()
                        .from(P)
                        .where(
                            P.COMPANY_ID.eq(query.companyId),
                            P.ACCOUNT_ID.eq(A.ID),
                            P.PERMISSION.eq(query.requiredPermission),
                        )
                ),
            )
            .orderBy(e.ACCOUNT_ID, e.ID)
            .limit(limit)
            .fetch { AnnouncementRecipientRow(it[e.ACCOUNT_ID]!!, it[e.ID]!!) }
    }
}
