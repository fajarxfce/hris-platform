import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveCarryExpiryId } from "../entities/leave-carry-expiry";
import {
  canReadLeaveCarryExpiries,
  isLeaveCarryExpiryCursor,
} from "../policies/leave-carry-expiry-policy";
import type { LeaveCarryExpiryRepository } from "../repositories/leave-carry-expiry-repository";
export class LoadLeaveCarryExpiry {
  constructor(private readonly expiries: LeaveCarryExpiryRepository) {}
  execute(access: CompanyAccess, id: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadLeaveCarryExpiries(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("leave_carry_expiry_not_found"));
    if (!isLeaveCarryExpiryCursor(after)) return Promise.resolve(failed("invalid_page"));
    return this.expiries.get(
      access.companyId,
      id.toLowerCase() as LeaveCarryExpiryId,
      after,
      signal,
    );
  }
}
