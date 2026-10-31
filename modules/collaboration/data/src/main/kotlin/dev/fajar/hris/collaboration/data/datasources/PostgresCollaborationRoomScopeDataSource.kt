package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationDirectConversations.COLLABORATION_DIRECT_CONVERSATIONS as D
import dev.fajar.hris.schema.tables.CollaborationDirectRoomBindings.COLLABORATION_DIRECT_ROOM_BINDINGS as B
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record

class PostgresCollaborationRoomScopeDataSource(private val sql: DSLContext) :
    CollaborationRoomScopeDataSource {
    override fun owned(
        companyId: UUID,
        accountId: UUID,
        limit: Int,
    ): List<CollaborationOwnedRoomData> {
        require(limit in 1..10_001)
        return projection()
            .where(
                D.COMPANY_ID.eq(companyId),
                D.FIRST_ACCOUNT_ID.eq(accountId).or(D.SECOND_ACCOUNT_ID.eq(accountId)),
            )
            .orderBy(D.ID)
            .limit(limit)
            .fetch { mapped(it) }
    }

    override fun find(
        companyId: UUID,
        accountId: UUID,
        serverName: String,
        roomId: String,
    ): CollaborationOwnedRoomData? =
        projection()
            .where(
                D.COMPANY_ID.eq(companyId),
                D.FIRST_ACCOUNT_ID.eq(accountId).or(D.SECOND_ACCOUNT_ID.eq(accountId)),
                B.MATRIX_SERVER_NAME.eq(serverName),
                B.MATRIX_ROOM_ID.eq(roomId),
            )
            .limit(2)
            .fetchOne { mapped(it) }

    private fun projection() =
        sql.select(
                D.COMPANY_ID,
                D.ID,
                D.FIRST_ACCOUNT_ID,
                D.SECOND_ACCOUNT_ID,
                B.ACCOUNT_ID,
                B.MATRIX_SERVER_NAME,
                B.MATRIX_ROOM_ID,
            )
            .from(D)
            .join(B)
            .on(B.COMPANY_ID.eq(D.COMPANY_ID), B.CONVERSATION_ID.eq(D.ID))

    private fun mapped(row: Record) =
        CollaborationOwnedRoomData(
            row.get(D.COMPANY_ID)!!,
            row.get(D.ID)!!,
            row.get(D.FIRST_ACCOUNT_ID)!!,
            row.get(D.SECOND_ACCOUNT_ID)!!,
            row.get(B.ACCOUNT_ID)!!,
            row.get(B.MATRIX_SERVER_NAME)!!,
            row.get(B.MATRIX_ROOM_ID)!!,
        )
}
