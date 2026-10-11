package dev.fajar.hris.people.delivery.responses

import java.time.Instant
import java.time.LocalDate

data class EmploymentTransferReviewResponse(
    val employee: EmployeeResponse,
    val sourceCompany: TransferCompanyResponse,
    val targetCompany: TransferCompanyResponse,
    val sourceDate: LocalDate,
    val targetDate: LocalDate,
    val evaluatedAt: Instant,
    val targetPermissions: List<String>,
)
