package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.core.domain.Page

data class LeavePolicyReview(val current: LeaveType, val history: Page<LeavePolicyRevision>)
