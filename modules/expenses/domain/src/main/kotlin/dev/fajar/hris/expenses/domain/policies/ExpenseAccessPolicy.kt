package dev.fajar.hris.expenses.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimReadScope

fun canReadOwnExpenses(permissions: Set<String>): Boolean =
    permissions.any { it in setOf("expenses.self.manage", "expenses.read", "expenses.manage") }

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
    scope: ExpenseClaimReadScope = ExpenseClaimReadScope.AUTHORIZED,
): Boolean =
    (scope == ExpenseClaimReadScope.AUTHORIZED || employee.person.accountId == actor.accountId) &&
        ("expenses.read" in actor.permissions ||
            canManageExpenseClaim(actor, employee) ||
            ("expenses.team.read" in actor.permissions &&
                employee.managerAccountId == actor.accountId &&
                employee.terms.isWorkingOn(today)))
