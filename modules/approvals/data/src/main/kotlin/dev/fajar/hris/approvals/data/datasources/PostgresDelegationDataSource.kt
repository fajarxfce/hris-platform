package dev.fajar.hris.approvals.data.datasources

import dev.fajar.hris.schema.tables.ApprovalDelegations.APPROVAL_DELEGATIONS as D
import dev.fajar.hris.schema.tables.records.ApprovalDelegationsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresDelegationDataSource(private val sql: DSLContext) : DelegationDataSource {
    override fun find(companyId: UUID, id: UUID): ApprovalDelegationsRecord? =
        sql.selectFrom(D).where(D.COMPANY_ID.eq(companyId)).and(D.ID.eq(id)).fetchOne()

    override fun forAccount(
        companyId: UUID,
        accountId: UUID,
        at: OffsetDateTime,
    ): List<ApprovalDelegationsRecord> =
        sql.selectFrom(D)
            .where(D.COMPANY_ID.eq(companyId))
            .and(D.FROM_ACCOUNT.eq(accountId).or(D.TO_ACCOUNT.eq(accountId)))
            .and(D.VALID_UNTIL.gt(at))
            .orderBy(D.ID)
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
