import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadTaxOpenings } from "../policies/tax-opening-access-policy";
import { isTaxYear } from "../policies/tax-opening-validation";
import type { TaxOpeningRepository } from "../repositories/tax-opening-repository";
export class LoadTaxOpeningRevision {
  constructor(private readonly openings: TaxOpeningRepository) {}
  execute(
    access: CompanyAccess,
    employee: string,
    year: string,
    revision: number,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadTaxOpenings(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(employee)) return Promise.resolve(failed("employee_not_found"));
    if (!isTaxYear(year) || !Number.isInteger(revision) || revision < 0 || revision > 999)
      return Promise.resolve(failed("invalid_page"));
    return this.openings.revision(access.companyId, employee.toLowerCase(), year, revision, signal);
  }
}
