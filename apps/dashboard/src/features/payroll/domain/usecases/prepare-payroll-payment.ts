import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentPreparation } from "../entities/payroll-payment-preparation";
import { preparePayrollPaymentCommand } from "../policies/payroll-payment-preparation-policy";
import type { PayrollPaymentRepository } from "../repositories/payroll-payment-repository";
export class PreparePayrollPayment {
  constructor(private readonly payments: PayrollPaymentRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    id: string,
    review: PayrollPaymentPreparation,
    itemIds: readonly string[],
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const command = preparePayrollPaymentCommand(access, operation, id, review, itemIds);
    if (!command.ok) return Promise.resolve(command);
    return this.payments.prepare(access.companyId, operation, command.value, signal);
  }
}
