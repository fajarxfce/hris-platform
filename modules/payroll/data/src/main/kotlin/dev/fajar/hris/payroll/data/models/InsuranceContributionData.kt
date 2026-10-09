package dev.fajar.hris.payroll.data.models

import java.time.*

data class InsuranceContributionData(
    val program: String,
    val base: String,
    val employeeAmount: String,
    val employerAmount: String,
)
