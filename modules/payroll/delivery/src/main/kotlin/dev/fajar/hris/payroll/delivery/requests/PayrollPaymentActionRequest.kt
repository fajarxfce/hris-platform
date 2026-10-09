package dev.fajar.hris.payroll.delivery.requests

data class PayrollPaymentActionRequest(val expectedVersion: Long, val reason: String)
