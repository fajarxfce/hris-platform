import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadLeaveCarryExpiries } from "../policies/leave-carry-expiry-policy";
import type { LeaveCarryExpiryRepository } from "../repositories/leave-carry-expiry-repository";
export class LoadLeaveCarryExpiries {
  constructor(private readonly expiries: LeaveCarryExpiryRepository) {}
  execute(access: CompanyAccess, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadLeaveCarryExpiries(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (after !== null && !isUuid(after)) return Promise.resolve(failed("invalid_page"));
    return this.expiries.list(access.companyId, after?.toLowerCase() ?? null, signal);
  }
}
