package dev.fajar.hris.leave.domain.entities

import java.util.UUID

data class LeaveEmployeeReference(val id: UUID, val number: String?, val name: String?)
