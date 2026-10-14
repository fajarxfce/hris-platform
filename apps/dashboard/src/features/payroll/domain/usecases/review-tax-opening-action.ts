import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { TaxOpeningAction } from "../entities/tax-opening-review";
import { isTaxYear } from "../policies/tax-opening-validation";
import type { TaxOpeningRepository } from "../repositories/tax-opening-repository";
export class ReviewTaxOpeningAction {
  constructor(private readonly openings: TaxOpeningRepository) {}
  async execute(
    access: CompanyAccess,
    employee: string,
    year: string,
    action: TaxOpeningAction,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!access.permissions.includes(action === "PREPARE" ? "payroll.calculate" : "payroll.review"))
      return failed("access_denied");
    if (!isUuid(employee)) return failed("employee_not_found");
    if (!isTaxYear(year)) return failed("invalid_payroll_tax_opening");
    const result = await this.openings.review(
      access.companyId,
      employee.toLowerCase(),
      year,
      null,
      signal,
    );
    signal.throwIfAborted();
    if (!result.ok) return result;
    return result.value.availableActions.includes(action)
      ? result
      : failed("payroll_tax_opening_action_unavailable");
  }
}
