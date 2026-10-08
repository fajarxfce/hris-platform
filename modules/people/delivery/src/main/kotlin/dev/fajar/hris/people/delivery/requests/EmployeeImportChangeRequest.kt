package dev.fajar.hris.people.delivery.requests

data class EmployeeImportChangeRequest(val expectedVersion: Long, val reason: String)
