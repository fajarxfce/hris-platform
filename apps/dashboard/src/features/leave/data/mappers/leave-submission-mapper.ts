import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { LeaveRequestSelection } from "../../domain/entities/leave-request-selection";
import type { LeaveRequestSubmission } from "../../domain/entities/leave-request-submission";
import type { LeaveReceiptDto } from "../models/leave-receipt-dto";
import type {
  LeaveRequestSelectionDto,
  LeaveRequestSubmissionDto,
} from "../models/leave-request-submission-dto";

export function toLeaveSelectionDto(selection: LeaveRequestSelection): LeaveRequestSelectionDto {
  return {
    employeeId: selection.employeeId,
    typeId: selection.typeId,
    days: selection.days.map((day) => ({ workDate: day.workDate, portion: day.portion })),
  };
}
export function toLeaveSubmissionDto(command: LeaveRequestSubmission): LeaveRequestSubmissionDto {
  return {
    ...toLeaveSelectionDto(command),
    id: command.id,
    reason: command.reason,
    attachmentRevisionIds: [...command.attachmentRevisionIds],
    previewRevision: command.previewRevision,
  };
}
export function toLeaveSubmissionReceipt(
  dto: LeaveReceiptDto,
  command: LeaveRequestSubmission,
): MutationReceipt {
  if (dto.id.toLowerCase() !== command.id || dto.version !== 0)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
