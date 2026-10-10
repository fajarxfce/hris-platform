import type { ApprovalRequest } from "../../features/approvals/domain/entities/approval-request";

export function approvalResourceRoute(request: ApprovalRequest): string | null {
  if (request.kind === "LEAVE" || request.kind === "LEAVE_CANCELLATION") {
    const query = new URLSearchParams({ company: request.companyId });
    return `/leave/requests/${encodeURIComponent(request.resourceId)}?${query}`;
  }
  return null;
}
