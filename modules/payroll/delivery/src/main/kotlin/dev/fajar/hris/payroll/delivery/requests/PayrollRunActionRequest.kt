package dev.fajar.hris.payroll.delivery.requests

import java.time.*

data class PayrollRunActionRequest(val expectedVersion: Long, val reason: String)
