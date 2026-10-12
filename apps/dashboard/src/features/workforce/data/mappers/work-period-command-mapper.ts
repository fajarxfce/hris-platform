import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { WorkPeriodIntent } from "../../domain/entities/work-period-action-context";
import type { WorkPeriodCommand } from "../../domain/entities/work-period-command";
import type { WorkPeriodCommandDto, WorkPeriodReceiptDto } from "../models/work-period-command-dto";

export function toWorkPeriodCommandDto(command: WorkPeriodCommand): WorkPeriodCommandDto {
  return { expectedVersion: command.expectedVersion, reason: command.reason };
}
export function toWorkPeriodReceipt(
  dto: WorkPeriodReceiptDto,
  command: WorkPeriodCommand,
  intent: WorkPeriodIntent,
) {
  if (
    dto.version !== command.expectedVersion + 1 ||
    (intent === "recover" && dto.id.toLowerCase() !== command.periodId)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
