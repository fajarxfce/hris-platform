import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollInputVerification } from "../entities/payroll-input-change";
import { validatePayrollInputVerification } from "../policies/payroll-input-validation";
import type { PayrollInputRepository } from "../repositories/payroll-input-repository";
export class VerifyPayrollInput {
  constructor(private readonly inputs: PayrollInputRepository) {}
  execute(
    access: CompanyAccess,
    employee: string,
    month: string,
    operation: OperationId,
    input: PayrollInputVerification,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!access.permissions.includes("payroll.review"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(employee) || !isUuid(operation))
      return Promise.resolve(failed("invalid_payroll_input"));
    const change = Object.freeze({ ...input, reason: input.reason.trim() });
    const failure = validatePayrollInputVerification(month, change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.inputs.verify(
      access.companyId,
      employee.toLowerCase(),
      month,
      operation,
      change,
      signal,
    );
  }
}
