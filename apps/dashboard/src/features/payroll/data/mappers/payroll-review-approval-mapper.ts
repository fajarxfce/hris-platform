import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { PayrollReviewApproval } from "../../domain/entities/payroll-review-approval";
import type { PayrollReviewApprovalDto } from "../models/payroll-review-approval-dto";

export function toPayrollReviewApproval(raw: PayrollReviewApprovalDto): PayrollReviewApproval {
  const stages = raw.stages.map((stage) => Object.freeze(stage.map((id) => id.toLowerCase()))),
    excluded = raw.excludedAccountIds.map((id) => id.toLowerCase());
  if (
    raw.currentStep >= stages.length ||
    stages.some((stage) => new Set(stage).size !== stage.length) ||
    new Set(excluded).size !== excluded.length
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    id: raw.id.toLowerCase(),
    templateId: raw.templateId.toLowerCase(),
    authorId: raw.authorId.toLowerCase(),
    stages: Object.freeze(stages),
    excludedAccountIds: Object.freeze(excluded),
  });
}
