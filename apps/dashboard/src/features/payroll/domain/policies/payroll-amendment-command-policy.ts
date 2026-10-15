import type { Failure } from "../../../../core/domain/result";
import type { PayrollAmendmentCommand } from "../entities/payroll-amendment-command";

export function validatePayrollAmendmentCommand(input: PayrollAmendmentCommand): Failure | null {
  if (
    !Number.isInteger(input.expectedVersion) ||
    input.expectedVersion < 0 ||
    input.expectedVersion > 998
  )
    return { code: "invalid_version", fields: {}, parameters: {} };
  if (!input.reason.trim() || input.reason.length > 1000 || /[\p{Cc}]/u.test(input.reason))
    return { code: "invalid_payroll_amendment", fields: { reason: "invalid" }, parameters: {} };
  return null;
}
