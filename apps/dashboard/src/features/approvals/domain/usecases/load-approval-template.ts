import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalTemplateId } from "../entities/approval-template";
import { canManageApprovals } from "../policies/approval-read-policy";
import { validApprovalRevision } from "../policies/approval-template-policy";
import type { ApprovalTemplateRepository } from "../repositories/approval-template-repository";

export class LoadApprovalTemplate {
  constructor(private readonly templates: ApprovalTemplateRepository) {}
  execute(access: CompanyAccess, id: string, revision: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageApprovals(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("approval_template_not_found"));
    if (!validApprovalRevision(revision)) return Promise.resolve(failed("invalid_revision"));
    return this.templates.get(
      access.companyId,
      id.toLowerCase() as ApprovalTemplateId,
      revision === null ? null : Number(revision),
      signal,
    );
  }
}
