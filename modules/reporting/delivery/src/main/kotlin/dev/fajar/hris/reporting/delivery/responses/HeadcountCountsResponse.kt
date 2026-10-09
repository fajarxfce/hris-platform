package dev.fajar.hris.reporting.delivery.responses

data class HeadcountCountsResponse(
    val employments: Long,
    val persons: Long,
    val active: Long,
    val probation: Long,
    val suspended: Long,
    val permanent: Long,
    val fixedTerm: Long,
)
