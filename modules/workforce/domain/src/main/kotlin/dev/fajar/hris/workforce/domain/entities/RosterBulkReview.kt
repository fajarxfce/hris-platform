package dev.fajar.hris.workforce.domain.entities

data class RosterBulkReview(
    val selection: RosterBulkSelection,
    val employees: List<RosterBulkEmployee>,
    val revision: String,
    val changeCount: Int,
)
