package dev.fajar.hris.reporting.domain.entities

/** Employment remains in headcount during suspension; this is not attendance eligibility. */
enum class HeadcountStatus {
    ACTIVE,
    PROBATION,
    SUSPENDED,
}
