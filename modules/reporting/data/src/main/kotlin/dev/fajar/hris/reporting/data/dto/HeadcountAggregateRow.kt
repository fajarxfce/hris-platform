package dev.fajar.hris.reporting.data.dto

data class HeadcountAggregateRow(
    val dimension: String,
    val key: String?,
    val employments: Long,
    val persons: Long,
)
