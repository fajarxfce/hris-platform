import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { TaxOpeningChange } from "../entities/tax-opening-change";
import { normalizeTaxOpening, validateTaxOpening } from "../policies/tax-opening-validation";
import type { TaxOpeningRepository } from "../repositories/tax-opening-repository";
export class SaveTaxOpening {
  constructor(private readonly openings: TaxOpeningRepository) {}
  execute(
    access: CompanyAccess,
    employee: string,
    year: string,
    operation: OperationId,
    input: TaxOpeningChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!access.permissions.includes("payroll.calculate"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(employee) || !isUuid(operation))
      return Promise.resolve(failed("invalid_payroll_tax_opening"));
    const change = normalizeTaxOpening(input);
    const failure = validateTaxOpening(year, change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.openings.save(
      access.companyId,
      employee.toLowerCase(),
      year,
      operation,
      change,
      signal,
    );
  }
}
