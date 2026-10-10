import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalAssigneeSearch } from "../entities/approval-assignee";
import { canManageApprovals, canReadApprovalInbox } from "../policies/approval-read-policy";
import { approvalActionPermissions, isApprovalKind } from "../policies/approval-template-policy";
import type { ApprovalAssigneeRepository } from "../repositories/approval-assignee-repository";

export class LoadApprovalAssignees {
  constructor(private readonly assignees: ApprovalAssigneeRepository) {}
  execute(access: CompanyAccess, search: ApprovalAssigneeSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (
      !isApprovalKind(search.kind) ||
      search.query.length > 120 ||
      (search.after !== null && !isUuid(search.after))
    )
      return Promise.resolve(failed("invalid_page"));
    if (
      !canManageApprovals(access.permissions) &&
      !(
        canReadApprovalInbox(access.permissions) &&
        approvalActionPermissions[search.kind].some((permission) =>
          access.permissions.includes(permission),
        )
      )
    )
      return Promise.resolve(failed("access_denied"));
    return this.assignees.list(
      access.companyId,
      Object.freeze({
        ...search,
        query: search.query.trim(),
        after: search.after?.toLowerCase() ?? null,
      }),
      signal,
    );
  }
}
