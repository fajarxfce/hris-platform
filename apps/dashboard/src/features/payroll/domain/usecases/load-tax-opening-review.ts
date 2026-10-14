import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { isPolicyCursor } from "../policies/payroll-policy-validation";
import { canReadTaxOpenings } from "../policies/tax-opening-access-policy";
import { isTaxYear } from "../policies/tax-opening-validation";
import type { TaxOpeningRepository } from "../repositories/tax-opening-repository";
export class LoadTaxOpeningReview {
  constructor(private readonly openings: TaxOpeningRepository) {}
  execute(
    access: CompanyAccess,
    employee: string,
    year: string,
    after: string | null,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadTaxOpenings(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(employee)) return Promise.resolve(failed("employee_not_found"));
    if (!isTaxYear(year) || !isPolicyCursor(after)) return Promise.resolve(failed("invalid_page"));
    return this.openings.review(access.companyId, employee.toLowerCase(), year, after, signal);
  }
}
