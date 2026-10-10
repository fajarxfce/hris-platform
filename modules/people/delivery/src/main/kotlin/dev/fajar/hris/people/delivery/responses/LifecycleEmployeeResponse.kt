package dev.fajar.hris.people.delivery.responses

import java.util.UUID

data class LifecycleEmployeeResponse(val id: UUID, val employeeNumber: String, val name: String)
