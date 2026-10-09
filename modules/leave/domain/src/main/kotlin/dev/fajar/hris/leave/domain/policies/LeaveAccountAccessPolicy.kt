package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.people.domain.entities.Employee
import dev.fajar.hris.people.domain.entities.EmploymentStatus
import java.util.UUID

fun canReadLeaveAccount(actor: Actor, current: Employee?, accountId: UUID?): Boolean =
    "leave.read" in actor.permissions ||
        ("leave.self.manage" in actor.permissions && accountId == actor.accountId) ||
        ("leave.team.read" in actor.permissions &&
            current?.managerAccountId == actor.accountId &&
            current.terms.status != EmploymentStatus.ENDED)
