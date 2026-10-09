package dev.fajar.hris.payroll.delivery.requests

data class PayrollInputVerificationRequest(val expectedVersion: Long, val reason: String)
