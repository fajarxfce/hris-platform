package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.Instant
import java.util.UUID

data class CollaborationSummaryCandidateRow(
    val companyId: UUID,
    val conversationId: UUID,
    val firstAccountId: UUID,
    val secondAccountId: UUID,
    val firstDisplayName: String,
    val secondDisplayName: String,
)

interface CollaborationSummaryProjectionDataSource {
    fun lockAccounts(now: Instant, limit: Int): List<CollaborationSummaryAccountQueueRecord>

    fun targets(
        account: UUID,
        afterCompany: UUID?,
        afterConversation: UUID?,
        limit: Int,
    ): List<CollaborationSummaryAccountTargetsRecord>

    fun enqueue(targets: List<CollaborationSummaryRefreshQueueRecord>): Boolean

    fun advanceAccount(
        account: UUID,
        afterCompany: UUID?,
        afterConversation: UUID?,
        next: Instant?,
    ): Boolean

    fun dueCompanies(now: Instant, limit: Int): List<UUID>

    fun lockDue(company: UUID, now: Instant, limit: Int): List<UUID>

    fun candidates(company: UUID, ids: List<UUID>): List<CollaborationSummaryCandidateRow>

    fun records(company: UUID, ids: List<UUID>): List<CollaborationDirectSummaryRecordsRecord>

    fun save(records: List<CollaborationDirectSummaryRecordsRecord>): Boolean

    fun complete(company: UUID, ids: List<UUID>): Boolean
}
