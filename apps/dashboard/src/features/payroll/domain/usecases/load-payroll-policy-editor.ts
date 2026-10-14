import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { IncomeTaxRule } from "../entities/income-tax-rule";
import type { PayrollPolicy } from "../entities/payroll-policy";
import { canManagePayrollPolicy } from "../policies/payroll-access-policy";
import { isPayrollMonth } from "../policies/payroll-policy-validation";
import type { PayrollPolicyRepository } from "../repositories/payroll-policy-repository";

export type PayrollPolicyEditorContext = Readonly<{
  current: PayrollPolicy | null;
  rules: readonly IncomeTaxRule[];
}>;
export class LoadPayrollPolicyEditor {
  constructor(private readonly policies: PayrollPolicyRepository) {}
  async execute(
    access: CompanyAccess,
    month: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollPolicyEditorContext>> {
    signal.throwIfAborted();
    if (!canManagePayrollPolicy(access.permissions)) return failed("access_denied");
    if (!isPayrollMonth(month)) return failed("invalid_payroll_month");
    const review = await this.policies.review(access.companyId, month, null, signal);
    signal.throwIfAborted();
    if (!review.ok) return review;
    const rules = await this.policies.rules(access.companyId, signal);
    signal.throwIfAborted();
    return rules.ok
      ? { ok: true, value: Object.freeze({ current: review.value.current, rules: rules.value }) }
      : rules;
  }
}
