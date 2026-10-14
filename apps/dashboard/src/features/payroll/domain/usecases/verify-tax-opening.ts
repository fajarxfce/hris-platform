import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { TaxOpeningVerification } from "../entities/tax-opening-change";
import { validateTaxOpeningVerification } from "../policies/tax-opening-validation";
import type { TaxOpeningRepository } from "../repositories/tax-opening-repository";
export class VerifyTaxOpening {
  constructor(private readonly openings: TaxOpeningRepository) {}
  execute(
    access: CompanyAccess,
    employee: string,
    year: string,
    operation: OperationId,
    input: TaxOpeningVerification,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!access.permissions.includes("payroll.review"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(employee) || !isUuid(operation))
      return Promise.resolve(failed("invalid_payroll_tax_opening"));
    const change = Object.freeze({ ...input, reason: input.reason.trim() });
    const failure = validateTaxOpeningVerification(year, change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.openings.verify(
      access.companyId,
      employee.toLowerCase(),
      year,
      operation,
      change,
      signal,
    );
  }
}
