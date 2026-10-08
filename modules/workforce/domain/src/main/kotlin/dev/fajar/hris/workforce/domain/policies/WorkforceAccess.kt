package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.people.domain.entities.Employee

fun canReadWorkforce(actor: Actor, employee: Employee): Boolean =
    "workforce.read" in actor.permissions ||
        ("workforce.team.read" in actor.permissions &&
            employee.managerAccountId == actor.accountId) ||
        ("attendance.self.record" in actor.permissions &&
            employee.person.accountId == actor.accountId)
