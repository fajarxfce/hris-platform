package dev.fajar.hris.workforce.domain.entities

/** The period and its current attempt are acquired from one database snapshot. */
data class WorkPeriodState(val period: WorkPeriod, val attempt: WorkPeriodAttempt?)
