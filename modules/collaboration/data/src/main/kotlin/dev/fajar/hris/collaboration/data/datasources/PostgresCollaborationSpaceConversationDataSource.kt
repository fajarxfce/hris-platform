package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpaceDirectConversations.COLLABORATION_SPACE_DIRECT_CONVERSATIONS as D
import dev.fajar.hris.schema.tables.CollaborationSpaceLegacyConversations.COLLABORATION_SPACE_LEGACY_CONVERSATIONS as L
import dev.fajar.hris.schema.tables.CollaborationSpaceMembers.COLLABORATION_SPACE_MEMBERS as M
import dev.fajar.hris.schema.tables.records.CollaborationSpaceDirectConversationsRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyConversationsRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceMembersRecord
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationSpaceConversationDataSource(private val sql: DSLContext) :
    CollaborationSpaceConversationDataSource {
    override fun member(spaceId: UUID, accountId: UUID): List<CollaborationSpaceMembersRecord> =
        sql.selectFrom(M).where(M.SPACE_ID.eq(spaceId), M.ACCOUNT_ID.eq(accountId)).limit(2).fetch()

    override fun lockPair(spaceId: UUID, first: UUID, second: UUID) {
        sql.execute(
            "select pg_advisory_xact_lock(hashtextextended(?,0))",
            "collaboration-space-direct:$spaceId:$first:$second",
        )
    }

    override fun findPair(
        spaceId: UUID,
        first: UUID,
        second: UUID,
    ): CollaborationSpaceDirectConversationsRecord? =
        sql.selectFrom(D)
            .where(
                D.SPACE_ID.eq(spaceId),
                D.FIRST_ACCOUNT_ID.eq(first),
                D.SECOND_ACCOUNT_ID.eq(second),
            )
            .fetchOne()

    override fun find(
        spaceId: UUID,
        accountId: UUID,
        id: UUID,
    ): CollaborationSpaceDirectConversationsRecord? =
        sql.selectFrom(D)
            .where(
                D.SPACE_ID.eq(spaceId),
                D.ID.eq(id),
                D.FIRST_ACCOUNT_ID.eq(accountId).or(D.SECOND_ACCOUNT_ID.eq(accountId)),
            )
            .fetchOne()

    override fun list(
        spaceId: UUID,
        accountId: UUID,
        after: UUID?,
        limit: Int,
    ): List<CollaborationSpaceDirectConversationsRecord> =
        sql.selectFrom(D)
            .where(
                D.SPACE_ID.eq(spaceId),
                D.FIRST_ACCOUNT_ID.eq(accountId).or(D.SECOND_ACCOUNT_ID.eq(accountId)),
                after?.let { D.ID.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(D.ID)
            .limit(limit)
            .fetch()

    override fun insert(record: CollaborationSpaceDirectConversationsRecord) {
        check(sql.insertInto(D).set(record).execute() == 1)
    }

    override fun legacy(
        spaceId: UUID,
        accountId: UUID,
        afterCompany: UUID?,
        afterConversation: UUID?,
        limit: Int,
    ): List<CollaborationSpaceLegacyConversationsRecord> =
        sql.selectFrom(L)
            .where(
                L.SPACE_ID.eq(spaceId),
                L.FIRST_ACCOUNT_ID.eq(accountId).or(L.SECOND_ACCOUNT_ID.eq(accountId)),
                if (afterCompany == null) DSL.noCondition()
                else
                    L.LEGACY_COMPANY_ID.gt(afterCompany)
                        .or(
                            L.LEGACY_COMPANY_ID.eq(afterCompany)
                                .and(L.LEGACY_CONVERSATION_ID.gt(afterConversation))
                        ),
            )
            .orderBy(L.LEGACY_COMPANY_ID, L.LEGACY_CONVERSATION_ID)
            .limit(limit)
            .fetch()

    override fun legacyPair(
        spaceId: UUID,
        accountId: UUID,
        companyId: UUID,
        first: UUID,
        second: UUID,
    ): List<CollaborationSpaceLegacyConversationsRecord> =
        sql.selectFrom(L)
            .where(
                L.SPACE_ID.eq(spaceId),
                L.LEGACY_COMPANY_ID.eq(companyId),
                L.FIRST_ACCOUNT_ID.eq(first),
                L.SECOND_ACCOUNT_ID.eq(second),
                L.FIRST_ACCOUNT_ID.eq(accountId).or(L.SECOND_ACCOUNT_ID.eq(accountId)),
            )
            .limit(2)
            .fetch()

    override fun legacyOne(
        spaceId: UUID,
        accountId: UUID,
        companyId: UUID,
        conversationId: UUID,
    ): CollaborationSpaceLegacyConversationsRecord? =
        sql.selectFrom(L)
            .where(
                L.SPACE_ID.eq(spaceId),
                L.LEGACY_COMPANY_ID.eq(companyId),
                L.LEGACY_CONVERSATION_ID.eq(conversationId),
                L.FIRST_ACCOUNT_ID.eq(accountId).or(L.SECOND_ACCOUNT_ID.eq(accountId)),
            )
            .fetchOne()
}
