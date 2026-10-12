package dev.fajar.hris.workforce.data.models

import java.util.UUID

data class WorkPeriodEmployeeRow(
    val id: UUID,
    val employeeNumber: String,
    val legalName: String,
    val status: String,
    val version: Long,
)
