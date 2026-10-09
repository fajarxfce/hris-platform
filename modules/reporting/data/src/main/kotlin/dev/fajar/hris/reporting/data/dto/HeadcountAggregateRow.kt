package dev.fajar.hris.reporting.data.dto

import java.util.UUID

data class HeadcountAggregateRow(
    val dimension: String,
    val key: String?,
    val employments: Long,
    val persons: Long,
    val companyId: UUID? = null,
)
