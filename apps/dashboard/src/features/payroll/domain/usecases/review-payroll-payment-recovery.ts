import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadPayrollPayments } from "../policies/payroll-payment-read-policy";
import type { PayrollPaymentRecoveryRepository } from "../repositories/payroll-payment-recovery-repository";

export class ReviewPayrollPaymentRecovery {
  constructor(private readonly recoveries: PayrollPaymentRecoveryRepository) {}
  async execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadPayrollPayments(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("payroll_payslip_not_found");
    const result = await this.recoveries.review(access.companyId, id.toLowerCase(), signal);
    signal.throwIfAborted();
    return result;
  }
}
