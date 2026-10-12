import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { overtimeActionSnapshot } from "../entities/overtime-action-snapshot";
import type { OvertimeRequestId } from "../entities/overtime-request";
import type { OvertimeAction } from "../entities/overtime-request-details";
import {
  canPerformOvertimeAction,
  hasOvertimeActionPermission,
} from "../policies/overtime-action-policy";
import type { OvertimeRepository } from "../repositories/overtime-repository";

export class ReviewOvertimeAction {
  constructor(private readonly requests: OvertimeRepository) {}
  async execute(access: CompanyAccess, id: string, action: OvertimeAction, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!hasOvertimeActionPermission(access.permissions, action)) return failed("access_denied");
    if (!isUuid(id)) return failed("overtime_request_not_found");
    const result = await this.requests.get(
      access.companyId,
      id.toLowerCase() as OvertimeRequestId,
      null,
      signal,
    );
    signal.throwIfAborted();
    if (
      result.ok &&
      !canPerformOvertimeAction(access, overtimeActionSnapshot(result.value), action)
    )
      return failed("overtime_action_unavailable");
    return result;
  }
}
