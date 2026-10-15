import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentDetails } from "../entities/payroll-amendment";
import type { PayrollAmendmentDraftAction } from "../entities/payroll-amendment-command";
import { canAuthorPayrollAmendments } from "../policies/payroll-amendment-authoring-policy";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";

export class LoadPayrollAmendmentAction {
  constructor(private readonly amendments: PayrollAmendmentRepository) {}
  async execute(
    access: CompanyAccess,
    id: string,
    action: PayrollAmendmentDraftAction,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentDetails>> {
    signal.throwIfAborted();
    if (!canAuthorPayrollAmendments(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("payroll_amendment_not_found");
    const result = await this.amendments.get(access.companyId, id.toLowerCase(), signal);
    signal.throwIfAborted();
    if (!result.ok) return result;
    if (!result.value.availableActions.includes(action))
      return failed("payroll_amendment_not_editable");
    return result;
  }
}
