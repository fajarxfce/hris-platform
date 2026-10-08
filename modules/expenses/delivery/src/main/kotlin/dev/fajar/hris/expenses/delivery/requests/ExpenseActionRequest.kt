package dev.fajar.hris.expenses.delivery.requests

data class ExpenseActionRequest(val expectedVersion: Long, val reason: String)
