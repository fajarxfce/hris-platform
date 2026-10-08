package dev.fajar.hris.expenses.domain.policies

import dev.fajar.hris.core.domain.*

fun canReadExpensePolicies(actor: Actor): Boolean =
    actor.permissions.any {
        it in
            setOf(
                "expenses.policy.manage",
                "expenses.read",
                "expenses.approve",
                "expenses.pay",
                "expenses.team.read",
                "expenses.team.approve",
                "expenses.self.manage",
                "expenses.manage",
            )
    }

fun canManageExpenseClaim(
    actor: Actor,
    employee: dev.fajar.hris.people.domain.entities.Employee,
): Boolean =
    "expenses.manage" in actor.permissions ||
        ("expenses.self.manage" in actor.permissions &&
            employee.person.accountId == actor.accountId)

fun canReadExpenseClaim(
    actor: Actor,
    employee: dev.fajar.hris.people.domain.entities.Employee,
    today: java.time.LocalDate,
): Boolean =
    "expenses.read" in actor.permissions ||
        canManageExpenseClaim(actor, employee) ||
        ("expenses.team.read" in actor.permissions &&
            employee.managerAccountId == actor.accountId &&
            employee.terms.isWorkingOn(today))
