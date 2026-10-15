package dev.fajar.hris.payroll.delivery.requests

data class RecoverPayrollAmendmentPublicationRequest(
    val expectedVersion: Long,
    val expectedJobVersion: Long,
    val reason: String,
)
