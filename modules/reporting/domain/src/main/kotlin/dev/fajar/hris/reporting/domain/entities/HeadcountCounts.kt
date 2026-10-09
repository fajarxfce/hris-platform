package dev.fajar.hris.reporting.domain.entities

data class HeadcountCounts(
    val employments: Long,
    val persons: Long,
    val active: Long,
    val probation: Long,
    val suspended: Long,
    val permanent: Long,
    val fixedTerm: Long,
)
