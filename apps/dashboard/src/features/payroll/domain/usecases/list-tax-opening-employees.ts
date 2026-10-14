import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { type TaxOpeningSearch, taxOpeningEmployeeStatuses } from "../entities/tax-opening-review";
import { canReadTaxOpenings } from "../policies/tax-opening-access-policy";
import { isTaxYear } from "../policies/tax-opening-validation";
import type { TaxOpeningRepository } from "../repositories/tax-opening-repository";
export class ListTaxOpeningEmployees {
  constructor(private readonly openings: TaxOpeningRepository) {}
  execute(access: CompanyAccess, search: TaxOpeningSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadTaxOpenings(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (
      !isTaxYear(search.year) ||
      search.query.length > 120 ||
      (search.status !== null && !taxOpeningEmployeeStatuses.includes(search.status)) ||
      (search.after !== null && (!search.after.trim() || search.after.length > 32))
    )
      return Promise.resolve(failed("invalid_page"));
    return this.openings.employees(
      access.companyId,
      Object.freeze({ ...search, query: search.query.trim() }),
      signal,
    );
  }
}
