import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalTemplateSearch } from "../entities/approval-template";
import { canManageApprovals } from "../policies/approval-read-policy";
import { validApprovalTemplateSearch } from "../policies/approval-template-policy";
import type { ApprovalTemplateRepository } from "../repositories/approval-template-repository";

export class LoadApprovalTemplates {
  constructor(private readonly templates: ApprovalTemplateRepository) {}
  execute(access: CompanyAccess, search: ApprovalTemplateSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageApprovals(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!validApprovalTemplateSearch(search)) return Promise.resolve(failed("invalid_page"));
    return this.templates.list(
      access.companyId,
      Object.freeze({ ...search, after: search.after?.toLowerCase() ?? null }),
      signal,
    );
  }
}
