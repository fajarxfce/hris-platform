package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationRecipientDevices.COLLABORATION_RECIPIENT_DEVICES as D
import dev.fajar.hris.schema.tables.records.CollaborationRecipientDevicesRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationRecipientDataSource(private val sql: DSLContext) :
    CollaborationRecipientDataSource {
    override fun devices(
        companyId: UUID,
        accountIds: Set<UUID>,
        at: OffsetDateTime,
        limit: Int,
    ): List<CollaborationRecipientDevicesRecord> =
        sql.selectFrom(D)
            .where(
                D.COMPANY_ID.eq(companyId),
                D.ACCOUNT_ID.`in`(accountIds),
                D.RECEIVE_UNTIL.gt(at),
            )
            .orderBy(D.ACCOUNT_ID, D.DEVICE_ID)
            .limit(limit)
            .fetch()
}
