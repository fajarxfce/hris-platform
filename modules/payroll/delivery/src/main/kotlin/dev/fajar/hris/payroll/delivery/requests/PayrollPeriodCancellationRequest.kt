package dev.fajar.hris.payroll.delivery.requests

data class PayrollPeriodCancellationRequest(val expectedVersion: Long, val reason: String)
