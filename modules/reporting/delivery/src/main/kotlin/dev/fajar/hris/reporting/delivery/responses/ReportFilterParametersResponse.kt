package dev.fajar.hris.reporting.delivery.responses

import java.util.UUID

data class ReportFilterParametersResponse(
    val asOf: String? = null,
    val from: String? = null,
    val until: String? = null,
    val status: String? = null,
    val companies: List<UUID> = emptyList(),
)
