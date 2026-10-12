import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OvertimeActionSnapshot } from "../entities/overtime-action-snapshot";
import { prepareOvertimeChange } from "../policies/overtime-action-policy";
import type { OvertimeRepository } from "../repositories/overtime-repository";

export class WithdrawOvertimeRequest {
  constructor(private readonly requests: OvertimeRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    review: OvertimeActionSnapshot,
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const change = prepareOvertimeChange(access, operation, review, "WITHDRAW", reason);
    if (!change.ok) return Promise.resolve(change);
    return this.requests.withdraw(access.companyId, operation, change.value, signal);
  }
}
