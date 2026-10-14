import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPayslipPage, PayrollPayslipSearch } from "../entities/payroll-payslip";
import {
  canReadPayrollPayslips,
  payrollPayslipSearchFailure,
} from "../policies/payroll-payslip-policy";
import type { PayrollPayslipRepository } from "../repositories/payroll-payslip-repository";

export class ListPayrollPayslips {
  constructor(private readonly payslips: PayrollPayslipRepository) {}
  execute(
    access: CompanyAccess,
    search: PayrollPayslipSearch,
    signal: AbortSignal,
  ): Promise<Result<PayrollPayslipPage>> {
    signal.throwIfAborted();
    if (!canReadPayrollPayslips(access.permissions))
      return Promise.resolve(failed("access_denied"));
    const failure = payrollPayslipSearchFailure(search);
    if (failure) return Promise.resolve({ ok: false, failure });
    return this.payslips.list(
      access.companyId,
      Object.freeze({
        ...search,
        query: search.query.trim(),
        employeeId: search.employeeId?.toLowerCase() ?? null,
        after: search.after?.toLowerCase() ?? null,
      }),
      signal,
    );
  }
}
