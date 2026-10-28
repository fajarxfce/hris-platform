package dev.fajar.hris.reporting.domain.entities

import java.util.UUID

/** Main-report selections only; cursors, result data and executable routes are never retained. */
data class ReportFilterParameters(
    val asOf: String? = null,
    val from: String? = null,
    val until: String? = null,
    val status: String? = null,
    val companies: List<UUID> = emptyList(),
)
