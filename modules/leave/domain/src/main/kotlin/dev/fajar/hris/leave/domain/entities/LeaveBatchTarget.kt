package dev.fajar.hris.leave.domain.entities

import java.util.UUID

data class LeaveBatchTarget(val ordinal: Int, val employeeId: UUID)
