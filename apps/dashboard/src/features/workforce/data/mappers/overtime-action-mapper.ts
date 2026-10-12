import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { OvertimeActualSubmission } from "../../domain/entities/overtime-actual-submission";
import type { OvertimeDecision } from "../../domain/entities/overtime-decision";
import type { OvertimeRequestChange } from "../../domain/entities/overtime-request-change";
import type { OvertimeActualDto } from "../models/overtime-actual-dto";
import type { OvertimeDecisionDto } from "../models/overtime-decision-dto";
import type { OvertimeReceiptDto } from "../models/overtime-receipt-dto";
import type { OvertimeWithdrawalDto } from "../models/overtime-withdrawal-dto";

export function toOvertimeActualDto(change: OvertimeActualSubmission): OvertimeActualDto {
  return {
    expectedVersion: change.version,
    reason: change.reason,
    actual: {
      startsAt: change.actual.startsAt,
      endsAt: change.actual.endsAt,
      breakMinutes: change.actual.breakMinutes,
    },
  };
}
export function toOvertimeDecisionDto(change: OvertimeDecision): OvertimeDecisionDto {
  return { expectedVersion: change.version, decision: change.decision, reason: change.reason };
}
export function toOvertimeWithdrawalDto(change: OvertimeRequestChange): OvertimeWithdrawalDto {
  return { expectedVersion: change.version, reason: change.reason };
}
export function toOvertimeReceipt(
  dto: OvertimeReceiptDto,
  change: OvertimeRequestChange,
): MutationReceipt {
  if (dto.id.toLowerCase() !== change.id.toLowerCase() || dto.version !== change.version + 1)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
