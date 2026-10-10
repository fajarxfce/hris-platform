import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { ApprovalId } from "../../../approvals/domain/entities/approval-request";
import type { LeaveWorkflow } from "../../domain/entities/leave-workflow";
import type { LeaveWorkflowDto } from "../models/leave-workflow-dto";

export function toLeaveWorkflow(dto: LeaveWorkflowDto): LeaveWorkflow {
  const stages = dto.stages.map((stage) => {
    const accounts = stage.map((id) => id.toLowerCase() as AccountId);
    if (new Set(accounts).size !== accounts.length) throw new InvalidHttpResponseError();
    return Object.freeze(accounts);
  });
  if (dto.currentStep >= stages.length) throw new InvalidHttpResponseError();
  return Object.freeze({
    ...dto,
    id: dto.id.toLowerCase() as ApprovalId,
    authorId: dto.authorId.toLowerCase() as AccountId,
    requesterId: (dto.requesterId?.toLowerCase() as AccountId | undefined) ?? null,
    stages: Object.freeze(stages),
  });
}
