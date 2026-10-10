package dev.fajar.hris.people.domain.entities

import java.util.UUID

data class LifecycleEmployeeReference(val id: UUID, val employeeNumber: String, val name: String)
