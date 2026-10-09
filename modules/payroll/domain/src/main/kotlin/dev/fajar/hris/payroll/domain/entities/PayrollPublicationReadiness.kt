package dev.fajar.hris.payroll.domain.entities

data class PayrollPublicationReadiness(
    val changedEmployments: Int,
    val duplicatePeople: Int,
    val assessedPeople: Int,
    val assessedHolidays: Int,
)
