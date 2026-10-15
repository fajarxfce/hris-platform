import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentCommand } from "../entities/payroll-amendment-command";
import { canAuthorPayrollAmendments } from "../policies/payroll-amendment-authoring-policy";
import { validatePayrollAmendmentCommand } from "../policies/payroll-amendment-command-policy";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";

export class CalculatePayrollAmendment {
  constructor(private readonly amendments: PayrollAmendmentRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    operation: OperationId,
    input: PayrollAmendmentCommand,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canAuthorPayrollAmendments(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id) || !isUuid(operation))
      return Promise.resolve(failed("invalid_payroll_amendment"));
    const normalized = Object.freeze({
        expectedVersion: input.expectedVersion,
        reason: input.reason.trim(),
      }),
      failure = validatePayrollAmendmentCommand(normalized);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.amendments.calculate(
      access.companyId,
      id.toLowerCase(),
      operation,
      normalized,
      signal,
    );
  }
}
