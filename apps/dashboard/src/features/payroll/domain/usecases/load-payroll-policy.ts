import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadPayrollPolicy } from "../policies/payroll-access-policy";
import { isPayrollMonth, isPolicyCursor } from "../policies/payroll-policy-validation";
import type { PayrollPolicyRepository } from "../repositories/payroll-policy-repository";

export class LoadPayrollPolicy {
  constructor(private readonly policies: PayrollPolicyRepository) {}
  execute(access: CompanyAccess, month: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadPayrollPolicy(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isPayrollMonth(month)) return Promise.resolve(failed("invalid_payroll_month"));
    if (!isPolicyCursor(after)) return Promise.resolve(failed("invalid_page"));
    return this.policies.review(access.companyId, month, after, signal);
  }
}
