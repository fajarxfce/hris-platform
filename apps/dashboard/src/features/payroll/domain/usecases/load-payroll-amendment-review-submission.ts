import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canAuthorPayrollAmendments } from "../policies/payroll-amendment-authoring-policy";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";

export class LoadPayrollAmendmentReviewSubmission {
  constructor(private readonly amendments: PayrollAmendmentRepository) {}
  async execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canAuthorPayrollAmendments(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("payroll_amendment_not_found");
    const result = await this.amendments.get(access.companyId, id.toLowerCase(), signal);
    signal.throwIfAborted();
    if (!result.ok) return result;
    if (
      result.value.amendment.summary.status !== "CALCULATED" ||
      !result.value.availableActions.includes("SUBMIT") ||
      result.value.latestCalculation?.status !== "SUCCEEDED"
    )
      return failed("payroll_amendment_calculation_required");
    return result;
  }
}
