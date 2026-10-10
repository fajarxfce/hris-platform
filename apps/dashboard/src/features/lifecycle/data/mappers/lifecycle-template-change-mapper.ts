import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { LifecycleTemplateChange } from "../../domain/entities/lifecycle-template-change";
import type {
  LifecycleTemplateChangeDto,
  LifecycleTemplateReceiptDto,
} from "../models/lifecycle-template-change-dto";

export function toLifecycleTemplateChangeDto(
  change: LifecycleTemplateChange,
): LifecycleTemplateChangeDto {
  return {
    expectedVersion: change.expectedVersion,
    code: change.code,
    name: change.name,
    kind: change.kind,
    active: change.active,
    reason: change.reason,
    tasks: change.tasks.map((task) => ({
      key: task.key,
      title: task.title,
      required: task.required,
      dueDays: task.dueDays,
    })),
  };
}
export function toLifecycleTemplateReceipt(
  dto: LifecycleTemplateReceiptDto,
  change: LifecycleTemplateChange,
): MutationReceipt {
  if (dto.id.toLowerCase() !== change.id || dto.version !== (change.expectedVersion ?? -1) + 1)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
