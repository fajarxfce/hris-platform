package dev.fajar.hris.people.delivery.responses

data class DirectoryDepartmentsResponse(
    val items: List<DirectoryDepartmentResponse>,
    val hasMore: Boolean,
)
