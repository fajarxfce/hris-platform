package dev.fajar.hris.payroll.data.models

data class PayrollPublicationReadinessRow(
    val changedEmployments: Int,
    val duplicatePeople: Int,
    val staleTaxHistories: Int,
    val assessedHolidays: Int,
)
