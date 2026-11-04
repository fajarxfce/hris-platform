package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpaceRecipientDevices.COLLABORATION_SPACE_RECIPIENT_DEVICES as D
import dev.fajar.hris.schema.tables.records.CollaborationSpaceRecipientDevicesRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationSpaceRecipientDataSource(private val sql: DSLContext) :
    CollaborationSpaceRecipientDataSource {
    override fun devices(
        spaceId: UUID,
        accountIds: Set<UUID>,
        at: OffsetDateTime,
        limit: Int,
    ): List<CollaborationSpaceRecipientDevicesRecord> =
        sql.selectFrom(D)
            .where(D.SPACE_ID.eq(spaceId), D.ACCOUNT_ID.`in`(accountIds), D.RECEIVE_UNTIL.gt(at))
            .orderBy(D.ACCOUNT_ID, D.DEVICE_ID)
            .limit(limit)
            .fetch()
}
