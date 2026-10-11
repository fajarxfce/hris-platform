package dev.fajar.hris.people.data.models

import java.util.UUID

data class DirectoryEmployeeRow(
    val id: UUID,
    val employeeNumber: String,
    val name: String,
    val position: String?,
    val department: String?,
)
