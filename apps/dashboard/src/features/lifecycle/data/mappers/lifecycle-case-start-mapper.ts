import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { LifecycleCaseStart } from "../../domain/entities/lifecycle-case-start";
import type {
  LifecycleCaseStartDto,
  LifecycleCaseStartReceiptDto,
} from "../models/lifecycle-case-start-dto";

export function toLifecycleCaseStartDto(command: LifecycleCaseStart): LifecycleCaseStartDto {
  return {
    id: command.id,
    employmentId: command.employmentId,
    templateId: command.templateId,
    templateVersion: command.templateVersion,
    targetDate: command.targetDate,
    assignees: { ...command.assignees },
    reason: command.reason,
  };
}
export function toLifecycleCaseStartReceipt(
  dto: LifecycleCaseStartReceiptDto,
  id: string,
): MutationReceipt {
  if (dto.id.toLowerCase() !== id || dto.version !== 0) throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
