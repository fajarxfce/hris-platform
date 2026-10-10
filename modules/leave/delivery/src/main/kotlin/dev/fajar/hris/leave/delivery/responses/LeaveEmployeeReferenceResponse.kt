package dev.fajar.hris.leave.delivery.responses

import java.util.UUID

data class LeaveEmployeeReferenceResponse(val id: UUID, val number: String?, val name: String?)
