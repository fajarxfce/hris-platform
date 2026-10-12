import type { OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OvertimeActionSnapshot } from "../entities/overtime-action-snapshot";
import type { OvertimeDecision } from "../entities/overtime-decision";
import { prepareOvertimeChange } from "../policies/overtime-action-policy";
import type { OvertimeRepository } from "../repositories/overtime-repository";

export class DecideOvertimeRequest {
  constructor(private readonly requests: OvertimeRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    review: OvertimeActionSnapshot,
    decision: OvertimeDecision["decision"],
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (decision !== "APPROVE" && decision !== "REJECT")
      return Promise.resolve(failed("invalid_overtime_decision"));
    const change = prepareOvertimeChange(access, operation, review, decision, reason);
    if (!change.ok) return Promise.resolve(change);
    return this.requests.decide(
      access.companyId,
      operation,
      Object.freeze({ ...change.value, decision }),
      signal,
    );
  }
}
