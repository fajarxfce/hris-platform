import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { ApprovalId } from "../../../approvals/domain/entities/approval-request";
import type { OvertimeRequestId } from "../../domain/entities/overtime-request";
import type { OvertimeRequestDetails } from "../../domain/entities/overtime-request-details";
import type { OvertimeDetailsDto } from "../models/overtime-details-dto";
import { toOvertimeHistory } from "./overtime-history-mapper";
import { toOvertimeRequest } from "./overtime-request-mapper";

export function toOvertimeDetails(
  dto: OvertimeDetailsDto,
  companyId: CompanyId,
  id: OvertimeRequestId,
  after: string | null,
): OvertimeRequestDetails {
  const request = toOvertimeRequest(dto.request, companyId);
  const stages = dto.approval?.stages.map((stage) => {
    const accounts = stage.map((account) => account.toLowerCase() as AccountId);
    if (new Set(accounts).size !== accounts.length) throw new InvalidHttpResponseError();
    return Object.freeze(accounts);
  });
  const approval =
    dto.approval !== null && stages !== undefined
      ? Object.freeze({
          ...dto.approval,
          id: dto.approval.id.toLowerCase() as ApprovalId,
          stages: Object.freeze(stages),
        })
      : null;
  if (
    request.id !== id ||
    (approval?.id ?? null) !== request.approvalId ||
    (approval !== null &&
      (approval.currentStep >= approval.stages.length ||
        (request.status === "PENDING"
          ? !["PENDING", "BLOCKED"].includes(approval.status)
          : approval.status !==
            (request.status === "WITHDRAWN" ? "CANCELLED" : request.status)))) ||
    new Set(dto.actions).size !== dto.actions.length ||
    dto.actions.some((action) =>
      action === "SUBMIT_ACTUAL"
        ? request.status !== "PLANNED"
        : action === "WITHDRAW"
          ? !["PLANNED", "PENDING"].includes(request.status)
          : request.status !== "PENDING",
    )
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    request,
    approval,
    history: toOvertimeHistory(dto.history, request, after),
    actions: Object.freeze([...dto.actions]),
  });
}
