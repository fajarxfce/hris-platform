package dev.fajar.hris.workforce.domain.entities

data class WorkPeriodEmployeeSnapshot(
    val employee: WorkforceEmployeeReference,
    val period: WorkPeriod,
    val snapshot: WorkPeriodSnapshot,
)
