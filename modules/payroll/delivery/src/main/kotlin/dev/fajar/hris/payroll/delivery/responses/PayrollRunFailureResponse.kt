package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class PayrollRunFailureResponse(
    val kind: String,
    val code: String,
    val fields: Map<String, String>,
    val parameters: Map<String, String>,
)
