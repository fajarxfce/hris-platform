import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePolicyChange } from "../entities/expense-policy-change";
import {
  normalizeExpensePolicyChange,
  validateExpensePolicyChange,
} from "../policies/expense-policy-change-policy";
import { canManageExpensePolicies } from "../policies/expense-read-policy";
import type { ExpensePolicyRepository } from "../repositories/expense-policy-repository";

export class SaveExpensePolicy {
  constructor(private readonly policies: ExpensePolicyRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: ExpensePolicyChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageExpensePolicies(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_expense_policy"));
    const change = normalizeExpensePolicyChange(input);
    const failure = validateExpensePolicyChange(change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.policies.save(access.companyId, operation, change, signal);
  }
}
