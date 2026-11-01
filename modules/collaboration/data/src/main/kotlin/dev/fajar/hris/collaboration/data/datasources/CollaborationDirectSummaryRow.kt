package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationDirectConversationsRecord

data class CollaborationDirectSummaryRow(
    val conversation: CollaborationDirectConversationsRecord,
    val peerDisplayName: String,
)
