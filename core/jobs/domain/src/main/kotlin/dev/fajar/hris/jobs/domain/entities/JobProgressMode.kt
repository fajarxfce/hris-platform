package dev.fajar.hris.jobs.domain.entities

/**
 * FIXED_TOTAL must finish every item; UPPER_BOUND may finish when its finite source is exhausted.
 */
enum class JobProgressMode {
    FIXED_TOTAL,
    UPPER_BOUND,
}
