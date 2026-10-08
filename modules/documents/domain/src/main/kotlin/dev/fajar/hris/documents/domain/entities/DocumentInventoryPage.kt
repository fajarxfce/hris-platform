package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

data class DocumentInventoryPage(
    val number: Int,
    val jobId: UUID,
    val counts: DocumentInventoryCounts,
    val hasMore: Boolean,
    val at: Instant,
)
