package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.leave.domain.entities.LeaveRequest
import dev.fajar.hris.people.domain.entities.Employee
import dev.fajar.hris.people.domain.entities.EmploymentStatus

fun canReadLeave(actor: Actor, employee: Employee, current: Employee?): Boolean =
    "leave.read" in actor.permissions ||
        ("leave.self.manage" in actor.permissions &&
            employee.person.accountId == actor.accountId) ||
        ("leave.team.read" in actor.permissions &&
            current?.managerAccountId == actor.accountId &&
            current.terms.status != EmploymentStatus.ENDED)

fun canReadLeaveTypes(actor: Actor): Boolean =
    actor.permissions.any {
        it in
            setOf(
                "leave.read",
                "leave.manage",
                "leave.self.manage",
                "leave.team.read",
                "leave.approve",
                "leave.team.approve",
            )
    }

/** Request ownership remains snapshotted when a person's current binding changes. */
fun canReadLeaveRequest(actor: Actor, request: LeaveRequest, current: Employee?): Boolean =
    "leave.read" in actor.permissions ||
        ("leave.self.manage" in actor.permissions && request.ownerAccountId == actor.accountId) ||
        (current != null && canReadLeave(actor, current, current))
