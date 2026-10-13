import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canPrepareLeave } from "../policies/leave-preparation-policy";
import type { LeavePreparationRepository } from "../repositories/leave-preparation-repository";

export class LoadLeavePreparationEvidence {
  constructor(private readonly preparation: LeavePreparationRepository) {}
  execute(access: CompanyAccess, employee: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canPrepareLeave(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(employee) || (after !== null && !isUuid(after)))
      return Promise.resolve(failed("invalid_page"));
    return this.preparation.evidence(
      access.companyId,
      employee.toLowerCase(),
      after?.toLowerCase() ?? null,
      signal,
    );
  }
}
