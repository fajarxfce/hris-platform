import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPolicyChange } from "../entities/payroll-policy-change";
import { canManagePayrollPolicy } from "../policies/payroll-access-policy";
import {
  normalizePayrollPolicy,
  validatePayrollPolicy,
} from "../policies/payroll-policy-validation";
import type { PayrollPolicyRepository } from "../repositories/payroll-policy-repository";

export class SavePayrollPolicy {
  constructor(private readonly policies: PayrollPolicyRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: PayrollPolicyChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManagePayrollPolicy(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_payroll_policy"));
    const change = normalizePayrollPolicy(input);
    const failure = validatePayrollPolicy(change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.policies.save(access.companyId, operation, change, signal);
  }
}
