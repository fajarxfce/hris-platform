import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveCarryExpiryResume } from "../entities/leave-carry-expiry-resume";
import { normalizeLeaveCarryExpiryResume } from "../policies/leave-carry-expiry-policy";
import type { LeaveCarryExpiryRepository } from "../repositories/leave-carry-expiry-repository";
export class ResumeLeaveCarryExpiry {
  constructor(private readonly expiries: LeaveCarryExpiryRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: LeaveCarryExpiryResume,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (input.companyId !== access.companyId || !access.permissions.includes("leave.year.close"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation) || operation.toLowerCase() === input.id.toLowerCase())
      return Promise.resolve(failed("invalid_leave_carry_expiry_recovery"));
    const command = normalizeLeaveCarryExpiryResume(input);
    return command.ok
      ? this.expiries.resume(operation, command.value, signal)
      : Promise.resolve(command);
  }
}
