package dev.fajar.hris.administration.data.datasources

import dev.fajar.hris.administration.data.dto.AuditQueryRow
import dev.fajar.hris.administration.data.mappers.toAuditEventRow
import dev.fajar.hris.schema.tables.AuditEntries.AUDIT_ENTRIES as A
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresAuditDataSource(private val sql: DSLContext) : AuditDataSource {
    override fun find(companyId: UUID, id: UUID) =
        sql.select(
                A.ID,
                A.COMPANY_ID,
                A.ACTOR_ID,
                A.RESOURCE_TYPE,
                A.RESOURCE_ID,
                A.ACTION,
                A.CORRELATION_ID,
                A.CREATED_AT,
            )
            .from(A)
            .where(A.COMPANY_ID.eq(companyId), A.ID.eq(id))
            .fetchOne { it.toAuditEventRow() }

    override fun search(companyId: UUID, query: AuditQueryRow) =
        sql.select(
                A.ID,
                A.COMPANY_ID,
                A.ACTOR_ID,
                A.RESOURCE_TYPE,
                A.RESOURCE_ID,
                A.ACTION,
                A.CORRELATION_ID,
                A.CREATED_AT,
            )
            .from(A)
            .where(A.COMPANY_ID.eq(companyId))
            .and(A.CREATED_AT.ge(query.from))
            .and(A.CREATED_AT.lt(query.until))
            .and(query.actorId?.let(A.ACTOR_ID::eq) ?: DSL.noCondition())
            .and(query.resourceType?.let(A.RESOURCE_TYPE::eq) ?: DSL.noCondition())
            .and(query.resourceId?.let(A.RESOURCE_ID::eq) ?: DSL.noCondition())
            .and(query.action?.let(A.ACTION::eq) ?: DSL.noCondition())
            .and(
                if (query.beforeAt == null) DSL.noCondition()
                else DSL.row(A.CREATED_AT, A.ID).lt(query.beforeAt, requireNotNull(query.beforeId))
            )
            .orderBy(A.CREATED_AT.desc(), A.ID.desc())
            .limit(query.limit)
            .fetch { it.toAuditEventRow() }
}
