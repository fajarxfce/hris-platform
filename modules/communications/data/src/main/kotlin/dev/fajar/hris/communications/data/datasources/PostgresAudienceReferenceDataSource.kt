package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.models.*
import dev.fajar.hris.schema.Tables.AUDIENCE_GROUP_HEADS as H
import dev.fajar.hris.schema.Tables.AUDIENCE_GROUP_REVISIONS as R
import dev.fajar.hris.schema.Tables.EMPLOYMENTS as E
import dev.fajar.hris.schema.Tables.ORGANIZATION_UNITS as U
import dev.fajar.hris.schema.Tables.PERSONS as P
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresAudienceReferenceDataSource(private val sql: DSLContext) :
    AudienceReferenceDataSource {
    override fun units(query: AudienceReferenceQuery, kind: String): List<AudienceReferenceRow> =
        sql.select(U.ID, U.NAME, U.CODE, U.VERSION, U.ACTIVE)
            .from(U)
            .where(U.COMPANY_ID.eq(query.companyId), U.KIND.eq(kind))
            .and(U.NAME.containsIgnoreCase(query.text).or(U.CODE.containsIgnoreCase(query.text)))
            .and(if (query.ids.isEmpty()) DSL.noCondition() else U.ID.`in`(query.ids))
            .and(query.active?.let { U.ACTIVE.eq(it) } ?: DSL.noCondition())
            .and(query.after?.let { U.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(U.ID)
            .limit(query.limit)
            .fetch {
                AudienceReferenceRow(
                    it[U.ID]!!,
                    it[U.NAME]!!,
                    it[U.CODE],
                    it[U.VERSION]!!,
                    it[U.ACTIVE],
                )
            }

    override fun groups(query: AudienceReferenceQuery): List<AudienceReferenceRow> =
        sql.select(H.ID, R.NAME, H.VERSION, R.ACTIVE)
            .from(H)
            .join(R)
            .on(H.COMPANY_ID.eq(R.COMPANY_ID), H.ID.eq(R.ID), H.VERSION.eq(R.VERSION))
            .where(H.COMPANY_ID.eq(query.companyId))
            .and(R.NAME.containsIgnoreCase(query.text))
            .and(if (query.ids.isEmpty()) DSL.noCondition() else H.ID.`in`(query.ids))
            .and(query.active?.let { R.ACTIVE.eq(it) } ?: DSL.noCondition())
            .and(query.after?.let { H.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(H.ID)
            .limit(query.limit)
            .fetch {
                AudienceReferenceRow(it[H.ID]!!, it[R.NAME]!!, null, it[H.VERSION]!!, it[R.ACTIVE])
            }

    override fun employments(query: AudienceReferenceQuery): List<AudienceReferenceRow> =
        sql.select(E.ID, P.LEGAL_NAME, E.EMPLOYEE_NUMBER, E.VERSION)
            .from(E)
            .join(P)
            .on(P.ID.eq(E.PERSON_ID))
            .where(E.COMPANY_ID.eq(query.companyId))
            .and(
                P.LEGAL_NAME.containsIgnoreCase(query.text)
                    .or(E.EMPLOYEE_NUMBER.containsIgnoreCase(query.text))
            )
            .and(if (query.ids.isEmpty()) DSL.noCondition() else E.ID.`in`(query.ids))
            .and(query.after?.let { E.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(E.ID)
            .limit(query.limit)
            .fetch {
                AudienceReferenceRow(
                    it[E.ID]!!,
                    it[P.LEGAL_NAME]!!,
                    it[E.EMPLOYEE_NUMBER],
                    it[E.VERSION]!!,
                    null,
                )
            }
}
