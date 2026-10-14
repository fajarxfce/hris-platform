package dev.fajar.hris.payroll.delivery.requests

data class PayrollAmendmentCommandRequest(val expectedVersion: Long, val reason: String)
