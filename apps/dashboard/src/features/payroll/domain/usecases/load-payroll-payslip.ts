import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPayslipScope } from "../entities/payroll-payslip";
import {
  canReadPayrollPayslips,
  validPayrollPayslipRevision,
  validPayrollPayslipScope,
} from "../policies/payroll-payslip-policy";
import type { PayrollPayslipRepository } from "../repositories/payroll-payslip-repository";

export class LoadPayrollPayslip {
  constructor(private readonly payslips: PayrollPayslipRepository) {}
  async execute(
    access: CompanyAccess,
    id: string,
    scope: PayrollPayslipScope,
    revision: number | null,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadPayrollPayslips(access.permissions)) return failed("access_denied");
    if (!isUuid(id) || !validPayrollPayslipScope(scope)) return failed("payroll_payslip_not_found");
    if (revision !== null && !validPayrollPayslipRevision(revision))
      return failed("invalid_payslip_revision");
    return this.payslips.get(access.companyId, id.toLowerCase(), scope, revision, signal);
  }
}
