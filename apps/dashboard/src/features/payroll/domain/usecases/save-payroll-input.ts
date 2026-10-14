import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollInputChange } from "../entities/payroll-input-change";
import { normalizePayrollInput, validatePayrollInput } from "../policies/payroll-input-validation";
import type { PayrollInputRepository } from "../repositories/payroll-input-repository";
export class SavePayrollInput {
  constructor(private readonly inputs: PayrollInputRepository) {}
  execute(
    access: CompanyAccess,
    employee: string,
    month: string,
    operation: OperationId,
    input: PayrollInputChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!access.permissions.includes("payroll.calculate"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(employee) || !isUuid(operation))
      return Promise.resolve(failed("invalid_payroll_input"));
    const change = normalizePayrollInput(input);
    const failure = validatePayrollInput(month, change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.inputs.save(
      access.companyId,
      employee.toLowerCase(),
      month,
      operation,
      change,
      signal,
    );
  }
}
