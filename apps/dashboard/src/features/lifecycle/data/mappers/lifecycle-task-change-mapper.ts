import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { LifecycleTaskChange } from "../../domain/entities/lifecycle-task-change";
import type {
  LifecycleTaskChangeDto,
  LifecycleTaskReceiptDto,
} from "../models/lifecycle-task-change-dto";

export function toLifecycleTaskChangeDto(change: LifecycleTaskChange): LifecycleTaskChangeDto {
  return {
    expectedVersion: change.expectedVersion,
    status: change.status,
    reason: change.reason,
  };
}
export function toLifecycleTaskReceipt(
  dto: LifecycleTaskReceiptDto,
  change: LifecycleTaskChange,
): MutationReceipt {
  if (dto.id.toLowerCase() !== change.caseId || dto.version !== change.expectedVersion + 1)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
