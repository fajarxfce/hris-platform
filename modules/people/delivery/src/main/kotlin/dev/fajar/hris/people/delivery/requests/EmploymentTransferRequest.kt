package dev.fajar.hris.people.delivery.requests

import java.util.UUID

data class EmploymentTransferRequest(
    val targetCompanyId: UUID,
    val targetEmploymentId: UUID,
    val expectedVersion: Long,
    val employeeNumber: String,
    val terms: TermsRequest,
    val reason: String,
)
