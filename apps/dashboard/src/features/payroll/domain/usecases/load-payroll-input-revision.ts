import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadPayrollInputs } from "../policies/payroll-input-access-policy";
import { isPayrollMonth } from "../policies/payroll-policy-validation";
import type { PayrollInputRepository } from "../repositories/payroll-input-repository";
export class LoadPayrollInputRevision {
  constructor(private readonly inputs: PayrollInputRepository) {}
  execute(
    access: CompanyAccess,
    employee: string,
    month: string,
    revision: number,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadPayrollInputs(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(employee)) return Promise.resolve(failed("employee_not_found"));
    if (!isPayrollMonth(month) || !Number.isInteger(revision) || revision < 0 || revision > 999)
      return Promise.resolve(failed("invalid_page"));
    return this.inputs.revision(access.companyId, employee.toLowerCase(), month, revision, signal);
  }
}
