import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  type PayrollInputSearch,
  payrollInputEmployeeStatuses,
} from "../entities/payroll-input-review";
import { canReadPayrollInputs } from "../policies/payroll-input-access-policy";
import { isPayrollMonth } from "../policies/payroll-policy-validation";
import type { PayrollInputRepository } from "../repositories/payroll-input-repository";
export class ListPayrollInputEmployees {
  constructor(private readonly inputs: PayrollInputRepository) {}
  execute(access: CompanyAccess, search: PayrollInputSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadPayrollInputs(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (
      !isPayrollMonth(search.month) ||
      search.query.length > 120 ||
      (search.status !== null && !payrollInputEmployeeStatuses.includes(search.status)) ||
      (search.after !== null && (!search.after.trim() || search.after.length > 32))
    )
      return Promise.resolve(failed("invalid_page"));
    return this.inputs.employees(
      access.companyId,
      Object.freeze({ ...search, query: search.query.trim() }),
      signal,
    );
  }
}
