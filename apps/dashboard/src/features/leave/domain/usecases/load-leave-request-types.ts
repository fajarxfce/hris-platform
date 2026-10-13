import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canPrepareLeave, isLeaveRequestDate } from "../policies/leave-preparation-policy";
import type { LeavePreparationRepository } from "../repositories/leave-preparation-repository";

export class LoadLeaveRequestTypes {
  constructor(private readonly preparation: LeavePreparationRepository) {}
  execute(access: CompanyAccess, asOf: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canPrepareLeave(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isLeaveRequestDate(asOf) || (after !== null && !/^[A-Z][A-Z0-9_-]{0,31}$/u.test(after)))
      return Promise.resolve(failed("invalid_page"));
    return this.preparation.types(access.companyId, asOf, after, signal);
  }
}
