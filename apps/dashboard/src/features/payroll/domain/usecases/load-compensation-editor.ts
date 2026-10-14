import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { CompensationEmployee } from "../entities/compensation-employee";
import type { EmployeeCompensation } from "../entities/employee-compensation";
import { canChangeCompensation } from "../policies/compensation-access-policy";
import { isPayrollMonth } from "../policies/payroll-policy-validation";
import type { CompensationRepository } from "../repositories/compensation-repository";

export type CompensationEditorContext = Readonly<{
  employee: CompensationEmployee;
  current: EmployeeCompensation | null;
}>;
export class LoadCompensationEditor {
  constructor(private readonly compensations: CompensationRepository) {}
  async execute(
    access: CompanyAccess,
    employee: string,
    month: string,
    signal: AbortSignal,
  ): Promise<Result<CompensationEditorContext>> {
    signal.throwIfAborted();
    if (!canChangeCompensation(access.permissions)) return failed("access_denied");
    if (!isUuid(employee)) return failed("employee_not_found");
    if (!isPayrollMonth(month)) return failed("invalid_payroll_month");
    const result = await this.compensations.review(
      access.companyId,
      employee.toLowerCase(),
      month,
      null,
      signal,
    );
    signal.throwIfAborted();
    if (!result.ok) return result;
    if (!result.value.canChange) return failed("compensation_change_unavailable");
    return {
      ok: true,
      value: Object.freeze({ employee: result.value.employee, current: result.value.current }),
    };
  }
}
