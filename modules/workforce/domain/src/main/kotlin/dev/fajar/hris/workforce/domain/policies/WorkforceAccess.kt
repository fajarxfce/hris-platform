package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.people.domain.entities.Employee
import dev.fajar.hris.people.domain.entities.EmploymentStatus

fun canReadWorkforce(actor: Actor, employee: Employee, current: Employee?): Boolean =
    "workforce.read" in actor.permissions ||
        ("workforce.team.read" in actor.permissions &&
            current?.managerAccountId == actor.accountId &&
            current.terms.status != EmploymentStatus.ENDED) ||
        ("attendance.self.record" in actor.permissions &&
            employee.person.accountId == actor.accountId)
