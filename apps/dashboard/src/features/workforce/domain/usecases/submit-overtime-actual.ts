import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OvertimeActionSnapshot } from "../entities/overtime-action-snapshot";
import type { OvertimeActualTime } from "../entities/overtime-actual-time";
import { prepareOvertimeChange } from "../policies/overtime-action-policy";
import { prepareOvertimeActual } from "../policies/overtime-actual-policy";
import type { OvertimeRepository } from "../repositories/overtime-repository";

export class SubmitOvertimeActual {
  constructor(private readonly requests: OvertimeRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    review: OvertimeActionSnapshot,
    input: OvertimeActualTime,
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const change = prepareOvertimeChange(access, operation, review, "SUBMIT_ACTUAL", reason);
    if (!change.ok) return Promise.resolve(change);
    const actual = prepareOvertimeActual(review.requested, input);
    if (!actual.ok) return Promise.resolve(actual);
    // Re-reading before receipt recovery could hide an already committed submission.
    return this.requests.submitActual(
      access.companyId,
      operation,
      Object.freeze({ ...change.value, actual: actual.value }),
      signal,
    );
  }
}
