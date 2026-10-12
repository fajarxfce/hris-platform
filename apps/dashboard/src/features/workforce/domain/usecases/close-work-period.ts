import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { WorkPeriodActionContext } from "../entities/work-period-action-context";
import { prepareWorkPeriodCommand } from "../policies/work-period-action-policy";
import type { WorkPeriodRepository } from "../repositories/work-period-repository";

export class CloseWorkPeriod {
  constructor(private readonly periods: WorkPeriodRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    context: WorkPeriodActionContext,
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const command = prepareWorkPeriodCommand(access, operation, context, "close", reason);
    return command.ok
      ? this.periods.close(access.companyId, operation, command.value, signal)
      : Promise.resolve(command);
  }
}
