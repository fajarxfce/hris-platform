package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

data class CollaborationSummaryFactRow(
    val record: CollaborationDirectSummaryRecordsRecord,
    val conversation: CollaborationDirectConversationsRecord,
)

interface CollaborationSummaryReadDataSource {
    fun head(company: UUID): MobileSyncHeadsRecord

    fun snapshot(
        company: UUID,
        account: UUID,
        after: UUID?,
        limit: Int,
    ): List<CollaborationSummaryFactRow>

    fun changes(
        company: UUID,
        account: UUID,
        after: Long,
        upper: Long,
        limit: Int,
    ): List<MobileSyncChangesRecord>

    fun facts(company: UUID, account: UUID, ids: List<UUID>): List<CollaborationSummaryFactRow>

    fun pending(company: UUID, account: UUID): Boolean
}
