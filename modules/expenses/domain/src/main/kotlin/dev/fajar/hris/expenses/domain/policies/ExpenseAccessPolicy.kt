package dev.fajar.hris.expenses.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimReadScope

fun canReadCompanyExpenses(permissions: Set<String>): Boolean =
    "expenses.read" in permissions || "expenses.manage" in permissions

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
        (canReadCompanyExpenses(actor.permissions) ||
            canManageExpenseClaim(actor, employee) ||
            ("expenses.team.read" in actor.permissions &&
                employee.managerAccountId == actor.accountId &&
                employee.terms.isWorkingOn(today)))

/** Scope and delegation time are observations, not a reservation for later delivery. */
fun validateExpenseReceiptRead(
    actor: Actor,
    employee: dev.fajar.hris.people.domain.entities.Employee?,
    today: java.time.LocalDate,
    scope: ExpenseClaimReadScope,
    approval: dev.fajar.hris.approvals.domain.entities.ApprovalRequest?,
    delegations: List<dev.fajar.hris.approvals.domain.entities.Delegation>,
    grants: List<dev.fajar.hris.identity.domain.entities.MemberAccount>,
    now: java.time.Instant,
): Result<Unit> =
    if (
        (employee != null && canReadExpenseClaim(actor, employee, today, scope)) ||
            (scope == ExpenseClaimReadScope.AUTHORIZED &&
                approval != null &&
                dev.fajar.hris.approvals.domain.policies.isAssignedApprover(
                    actor,
                    approval,
                    delegations,
                    grants,
                    now,
                ))
    )
        Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.NOT_FOUND, "expense_receipt_not_found"))
