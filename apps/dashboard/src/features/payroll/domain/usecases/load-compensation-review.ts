import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadCompensation } from "../policies/compensation-access-policy";
import { isPayrollMonth, isPolicyCursor } from "../policies/payroll-policy-validation";
import type { CompensationRepository } from "../repositories/compensation-repository";

export class LoadCompensationReview {
  constructor(private readonly compensations: CompensationRepository) {}
  execute(
    access: CompanyAccess,
    employee: string,
    month: string,
    after: string | null,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadCompensation(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(employee)) return Promise.resolve(failed("employee_not_found"));
    if (!isPayrollMonth(month)) return Promise.resolve(failed("invalid_payroll_month"));
    if (!isPolicyCursor(after)) return Promise.resolve(failed("invalid_page"));
    return this.compensations.review(
      access.companyId,
      employee.toLowerCase(),
      month,
      after,
      signal,
    );
  }
}
