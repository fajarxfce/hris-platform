import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBatchCreation } from "../entities/leave-batch-command";
import { canRunLeaveBatch, normalizeLeaveBatchCreation } from "../policies/leave-batch-policy";
import type { LeaveBatchRepository } from "../repositories/leave-batch-repository";
export class StartLeaveBatch {
  constructor(private readonly batches: LeaveBatchRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: LeaveBatchCreation,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (input.companyId !== access.companyId || !canRunLeaveBatch(access.permissions, input.kind))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation) || operation.toLowerCase() === input.id.toLowerCase())
      return Promise.resolve(failed("invalid_leave_batch"));
    const command = normalizeLeaveBatchCreation(input);
    return command.ok
      ? this.batches.start(operation, command.value, signal)
      : Promise.resolve(command);
  }
}
