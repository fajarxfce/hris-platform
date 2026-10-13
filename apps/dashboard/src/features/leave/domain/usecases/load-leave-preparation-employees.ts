import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canPrepareLeave } from "../policies/leave-preparation-policy";
import type { LeavePreparationRepository } from "../repositories/leave-preparation-repository";

export class LoadLeavePreparationEmployees {
  constructor(private readonly preparation: LeavePreparationRepository) {}
  execute(access: CompanyAccess, query: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canPrepareLeave(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (query.length > 120 || (after !== null && (after.trim().length === 0 || after.length > 32)))
      return Promise.resolve(failed("invalid_page"));
    return this.preparation.employees(access.companyId, query.trim(), after, signal);
  }
}
