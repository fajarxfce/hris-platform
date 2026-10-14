package dev.fajar.hris.people.delivery.responses

import java.util.UUID

data class DirectoryRecordResponse(
    val id: UUID,
    val version: Long,
    val employeeNumber: String,
    val name: String,
    val positionId: UUID?,
    val position: String?,
    val departmentId: UUID?,
    val department: String?,
)
