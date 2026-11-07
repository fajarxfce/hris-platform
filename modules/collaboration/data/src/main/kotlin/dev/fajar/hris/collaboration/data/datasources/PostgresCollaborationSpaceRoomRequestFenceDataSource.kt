package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpaceRoomRequestFences.COLLABORATION_SPACE_ROOM_REQUEST_FENCES as F
import dev.fajar.hris.schema.tables.records.CollaborationSpaceRoomRequestFencesRecord
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationSpaceRoomRequestFenceDataSource(private val sql: DSLContext) :
    CollaborationSpaceRoomRequestFenceDataSource {
    override fun find(
        accountId: UUID,
        operationId: UUID,
    ): CollaborationSpaceRoomRequestFencesRecord? =
        sql.selectFrom(F)
            .where(F.ACCOUNT_ID.eq(accountId), F.OPERATION_ID.eq(operationId))
            .fetchOne()

    override fun insert(value: CollaborationSpaceRoomRequestFencesRecord) {
        check(sql.insertInto(F).set(value).execute() == 1)
    }
}
