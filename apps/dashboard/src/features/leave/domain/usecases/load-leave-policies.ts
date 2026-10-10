import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeavePolicyQuery } from "../entities/leave-policy-definition";
import { canManageLeavePolicies } from "../policies/leave-read-policy";
import type { LeavePolicyRepository } from "../repositories/leave-policy-repository";

export class LoadLeavePolicies {
  constructor(private readonly policies: LeavePolicyRepository) {}
  execute(access: CompanyAccess, query: LeavePolicyQuery, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageLeavePolicies(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (
      (query.active !== null && !["true", "false"].includes(query.active)) ||
      (query.after !== null && !/^[A-Z][A-Z0-9_-]{0,31}$/u.test(query.after))
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
