import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePolicyQuery } from "../entities/expense-policy-definition";
import { canManageExpensePolicies } from "../policies/expense-read-policy";
import type { ExpensePolicyRepository } from "../repositories/expense-policy-repository";

export class LoadExpensePolicies {
  constructor(private readonly policies: ExpensePolicyRepository) {}
  execute(access: CompanyAccess, query: ExpensePolicyQuery, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageExpensePolicies(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (
      (query.active !== null && !["true", "false"].includes(query.active)) ||
      (query.after !== null && !/^[A-Z][A-Z0-9_]{1,31}$/u.test(query.after))
    )
      return Promise.resolve(failed("invalid_page"));
    return this.policies.list(
      access.companyId,
      query.active === null ? null : query.active === "true",
      query.after,
      signal,
    );
  }
}
