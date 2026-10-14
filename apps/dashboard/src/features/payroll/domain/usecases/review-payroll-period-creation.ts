import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPeriodCreation } from "../entities/payroll-period-creation";
import {
  canPreparePayrollPeriod,
  normalizePayrollPeriodCreation,
  validatePayrollPeriodCreation,
} from "../policies/payroll-period-policy";
export class ReviewPayrollPeriodCreation {
  execute(access: CompanyAccess, input: PayrollPeriodCreation) {
    if (!canPreparePayrollPeriod(access.permissions)) return failed("access_denied");
    const failure = validatePayrollPeriodCreation(input);
    return failure
      ? { ok: false as const, failure }
      : success(normalizePayrollPeriodCreation(input));
  }
}
