package dev.fajar.hris.reporting.delivery.requests

import java.util.UUID

data class ReportFilterParametersRequest(
    val asOf: String? = null,
    val from: String? = null,
    val until: String? = null,
    val status: String? = null,
    val companies: List<UUID> = emptyList(),
)
