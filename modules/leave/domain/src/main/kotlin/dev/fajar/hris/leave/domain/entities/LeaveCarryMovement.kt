package dev.fajar.hris.leave.domain.entities

import java.util.UUID

data class LeaveCarryMovement(val bucketId: UUID, val effect: LeaveCarryEffect, val halfDays: Int)
