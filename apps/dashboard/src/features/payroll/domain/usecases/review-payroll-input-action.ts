import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollInputAction } from "../entities/payroll-input-review";
import { isPayrollMonth } from "../policies/payroll-policy-validation";
import type { PayrollInputRepository } from "../repositories/payroll-input-repository";
export class ReviewPayrollInputAction {
  constructor(private readonly inputs: PayrollInputRepository) {}
  async execute(
    access: CompanyAccess,
    employee: string,
    month: string,
    action: PayrollInputAction,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!access.permissions.includes(action === "PREPARE" ? "payroll.calculate" : "payroll.review"))
      return failed("access_denied");
    if (!isUuid(employee)) return failed("employee_not_found");
    if (!isPayrollMonth(month)) return failed("invalid_payroll_month");
    const result = await this.inputs.review(
      access.companyId,
      employee.toLowerCase(),
      month,
      null,
      signal,
    );
    signal.throwIfAborted();
    if (!result.ok) return result;
    return result.value.availableActions.includes(action)
      ? result
      : failed("payroll_input_action_unavailable");
  }
}
