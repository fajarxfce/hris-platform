import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPayslipScope } from "../entities/payroll-payslip";
import {
  canReadPayrollPayslips,
  validPayrollPayslipScope,
} from "../policies/payroll-payslip-policy";
import type { PayrollPayslipRepository } from "../repositories/payroll-payslip-repository";

export class LoadPayrollPayslipPayments {
  constructor(private readonly payslips: PayrollPayslipRepository) {}
  async execute(
    access: CompanyAccess,
    id: string,
    scope: PayrollPayslipScope,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadPayrollPayslips(access.permissions)) return failed("access_denied");
    if (!isUuid(id) || !validPayrollPayslipScope(scope)) return failed("payroll_payslip_not_found");
    return this.payslips.payments(access.companyId, id.toLowerCase(), scope, signal);
  }
}
