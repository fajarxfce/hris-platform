import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { LeaveRequestChange } from "../../domain/entities/leave-request-action";
import type { LeaveReceiptDto } from "../models/leave-request-change-dto";

export function toLeaveReceipt(
  receipt: LeaveReceiptDto,
  command: LeaveRequestChange,
): MutationReceipt {
  if (receipt.id.toLowerCase() !== command.id || receipt.version !== command.version + 1)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
}
