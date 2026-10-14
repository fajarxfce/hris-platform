import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentOutcome } from "../entities/payroll-payment-outcome";
import type { PayrollPaymentReconciliationSnapshot } from "../entities/payroll-payment-reconciliation-snapshot";
import { preparePayrollPaymentReconciliation } from "../policies/payroll-payment-reconciliation-policy";
import type { PayrollPaymentRepository } from "../repositories/payroll-payment-repository";
export class ReconcilePayrollPayment {
  constructor(private readonly payments: PayrollPaymentRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    snapshot: PayrollPaymentReconciliationSnapshot,
    results: readonly PayrollPaymentOutcome[],
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const prepared = preparePayrollPaymentReconciliation(
      access,
      operation,
      snapshot,
      results,
      reason,
    );
    return prepared.ok
      ? this.payments.reconcile(access.companyId, operation, prepared.value, signal)
      : Promise.resolve(prepared);
  }
}
