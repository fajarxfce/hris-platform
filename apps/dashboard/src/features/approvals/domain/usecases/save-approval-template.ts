import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalTemplateChange } from "../entities/approval-template-change";
import { canManageApprovals } from "../policies/approval-read-policy";
import {
  normalizeApprovalTemplate,
  validApprovalTemplateChange,
} from "../policies/approval-template-policy";
import type { ApprovalTemplateRepository } from "../repositories/approval-template-repository";

export class SaveApprovalTemplate {
  constructor(private readonly templates: ApprovalTemplateRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: ApprovalTemplateChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageApprovals(access.permissions)) return Promise.resolve(failed("access_denied"));
    const change = normalizeApprovalTemplate(input);
    if (!isUuid(operation) || !validApprovalTemplateChange(change))
      return Promise.resolve(failed("invalid_approval_template"));
    return this.templates.save(access.companyId, operation, change, signal);
  }
}
