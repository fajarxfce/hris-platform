import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentSnapshot } from "../entities/payroll-payment-snapshot";
import type { PayrollPaymentTransition } from "../entities/payroll-payment-transition";
import { preparePayrollPaymentTransition } from "../policies/payroll-payment-transition-policy";
import type { PayrollPaymentRepository } from "../repositories/payroll-payment-repository";
export class TransitionPayrollPayment {
  constructor(private readonly payments: PayrollPaymentRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    snapshot: PayrollPaymentSnapshot,
    transition: PayrollPaymentTransition,
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const prepared = preparePayrollPaymentTransition(
      access,
      operation,
      snapshot,
      transition,
      reason,
    );
    return prepared.ok
      ? this.payments.transition(access.companyId, operation, prepared.value, signal)
      : Promise.resolve(prepared);
  }
}
