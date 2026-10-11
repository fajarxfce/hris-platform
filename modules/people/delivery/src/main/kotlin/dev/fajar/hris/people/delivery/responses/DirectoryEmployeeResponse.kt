package dev.fajar.hris.people.delivery.responses

import java.util.UUID

data class DirectoryEmployeeResponse(
    val id: UUID,
    val employeeNumber: String,
    val name: String,
    val position: String?,
    val department: String?,
)
