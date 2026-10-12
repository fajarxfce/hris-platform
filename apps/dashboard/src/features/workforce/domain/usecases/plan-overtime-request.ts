import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OvertimeIntervalInput } from "../entities/overtime-interval";
import type { OvertimePlanContext } from "../entities/overtime-plan-context";
import { prepareOvertimePlan } from "../policies/overtime-planning-policy";
import type { OvertimeRepository } from "../repositories/overtime-repository";

export class PlanOvertimeRequest {
  constructor(private readonly requests: OvertimeRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    id: string,
    review: OvertimePlanContext,
    requested: OvertimeIntervalInput,
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const plan = prepareOvertimePlan(access, operation, id, review, requested, reason);
    // A fresh read before an explicit retry could replace the original command's reviewed facts.
    return plan.ok
      ? this.requests.plan(access.companyId, operation, plan.value, signal)
      : Promise.resolve(plan);
  }
}
