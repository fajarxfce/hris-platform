package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.LeaveCalendarScope

/** TEAM narrows company authority too; self-service and approval alone grant no calendar. */
fun authorizeLeaveCalendar(actor: Actor, scope: LeaveCalendarScope): Result<Unit> =
    if (
        "leave.read" in actor.permissions ||
            (scope == LeaveCalendarScope.TEAM && "leave.team.read" in actor.permissions)
    )
        Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
