package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.jobs.domain.entities.BackgroundJob

data class LeaveCarryExpiryDetails(
    val overview: LeaveCarryExpiryOverview,
    val attempts: List<LeaveCarryExpiryAttempt>,
    val job: BackgroundJob,
    val results: Page<LeaveCarryExpiryOutcome>,
    val employees: List<LeaveEmployeeReference>,
    val availableActions: Set<LeaveCarryExpiryAction>,
)
