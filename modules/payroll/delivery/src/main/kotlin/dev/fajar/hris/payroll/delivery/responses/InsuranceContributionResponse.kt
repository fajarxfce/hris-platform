package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class InsuranceContributionResponse(
    val program: String,
    val base: String,
    val employeeAmount: String,
    val employerAmount: String,
)
