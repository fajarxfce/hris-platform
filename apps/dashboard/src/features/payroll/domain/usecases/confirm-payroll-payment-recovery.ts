import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentRecoveryInput } from "../entities/payroll-payment-recovery-input";
import type { PayrollPaymentRecoverySnapshot } from "../entities/payroll-payment-recovery-snapshot";
import { preparePayrollPaymentRecovery } from "../policies/payroll-payment-recovery-policy";
import type { PayrollPaymentRecoveryRepository } from "../repositories/payroll-payment-recovery-repository";

export class ConfirmPayrollPaymentRecovery {
  constructor(private readonly recoveries: PayrollPaymentRecoveryRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    id: string,
    snapshot: PayrollPaymentRecoverySnapshot,
    input: PayrollPaymentRecoveryInput,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const command = preparePayrollPaymentRecovery(access, operation, id, snapshot, input);
    return command.ok
      ? this.recoveries.confirm(access.companyId, operation, command.value, signal)
      : Promise.resolve(command);
  }
}
