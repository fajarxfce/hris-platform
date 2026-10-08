package dev.fajar.hris.approvals.data.datasources

import dev.fajar.hris.schema.tables.ApprovalDelegations.APPROVAL_DELEGATIONS as D
import dev.fajar.hris.schema.tables.records.ApprovalDelegationsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresDelegationDataSource(private val sql: DSLContext) : DelegationDataSource {
    override fun find(companyId: UUID, id: UUID): ApprovalDelegationsRecord? =
        sql.selectFrom(D).where(D.COMPANY_ID.eq(companyId)).and(D.ID.eq(id)).fetchOne()

    override fun countUnexpired(
        companyId: UUID,
        accountId: UUID,
        at: OffsetDateTime,
        activeOnly: Boolean,
        exceptId: UUID,
    ): Int =
        sql.fetchCount(
            D,
            D.COMPANY_ID.eq(companyId)
                .and(D.FROM_ACCOUNT.eq(accountId).or(D.TO_ACCOUNT.eq(accountId)))
                .and(D.VALID_UNTIL.gt(at))
                .and(D.ID.ne(exceptId))
                .and(if (activeOnly) D.ACTIVE.isTrue else DSL.noCondition()),
        )

    override fun forAccount(
        companyId: UUID,
        accountId: UUID,
        at: OffsetDateTime,
        activeOnly: Boolean,
        after: UUID?,
        limit: Int,
    ): List<ApprovalDelegationsRecord> =
        sql.selectFrom(D)
            .where(D.COMPANY_ID.eq(companyId))
            .and(D.FROM_ACCOUNT.eq(accountId).or(D.TO_ACCOUNT.eq(accountId)))
            .and(D.VALID_UNTIL.gt(at))
            .and(if (activeOnly) D.ACTIVE.isTrue.and(D.VALID_FROM.le(at)) else DSL.noCondition())
            .and(after?.let { D.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(D.ID)
            .limit(limit)
            .fetch()

    override fun insert(row: ApprovalDelegationsRecord) {
        sql.insertInto(D).set(row).execute()
    }

    override fun update(row: ApprovalDelegationsRecord, expectedVersion: Long): Long? =
        sql.update(D)
            .set(D.KIND, row.kind)
            .set(D.FROM_ACCOUNT, row.fromAccount)
            .set(D.TO_ACCOUNT, row.toAccount)
            .set(D.VALID_FROM, row.validFrom)
            .set(D.VALID_UNTIL, row.validUntil)
            .set(D.ACTIVE, row.active)
            .set(D.VERSION, expectedVersion + 1)
            .where(D.COMPANY_ID.eq(row.companyId))
            .and(D.ID.eq(row.id))
            .and(D.VERSION.eq(expectedVersion))
            .returning(D.VERSION)
            .fetchOne()
            ?.version
}
