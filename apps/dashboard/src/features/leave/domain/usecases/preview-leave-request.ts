import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveRequestSelection } from "../entities/leave-request-selection";
import { canPrepareLeave, validateLeaveSelection } from "../policies/leave-preparation-policy";
import type { LeavePreparationRepository } from "../repositories/leave-preparation-repository";

export class PreviewLeaveRequest {
  constructor(private readonly preparation: LeavePreparationRepository) {}
  async execute(access: CompanyAccess, input: LeaveRequestSelection, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canPrepareLeave(access.permissions)) return failed("access_denied");
    const selected = validateLeaveSelection(input);
    if (!selected.ok) return selected;
    const result = await this.preparation.preview(access.companyId, selected.value, signal);
    signal.throwIfAborted();
    return result.ok
      ? success(Object.freeze({ selection: selected.value, preview: result.value }))
      : result;
  }
}
