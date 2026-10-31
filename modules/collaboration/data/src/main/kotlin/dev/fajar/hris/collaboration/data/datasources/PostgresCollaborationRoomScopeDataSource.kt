package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationDirectConversations.COLLABORATION_DIRECT_CONVERSATIONS as D
import dev.fajar.hris.schema.tables.CollaborationDirectRoomBindings.COLLABORATION_DIRECT_ROOM_BINDINGS as B
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationRoomScopeDataSource(private val sql: DSLContext) :
    CollaborationRoomScopeDataSource {
    override fun owned(
        companyId: UUID,
        accountId: UUID,
        limit: Int,
    ): List<CollaborationOwnedRoomData> {
        require(limit in 1..10_001)
        return sql.select(
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
            .where(
                D.COMPANY_ID.eq(companyId),
                D.FIRST_ACCOUNT_ID.eq(accountId).or(D.SECOND_ACCOUNT_ID.eq(accountId)),
            )
            .orderBy(D.ID)
            .limit(limit)
            .fetch {
                CollaborationOwnedRoomData(
                    it.value1()!!,
                    it.value2()!!,
                    it.value3()!!,
                    it.value4()!!,
                    it.value5()!!,
                    it.value6()!!,
                    it.value7()!!,
                )
            }
    }
}
