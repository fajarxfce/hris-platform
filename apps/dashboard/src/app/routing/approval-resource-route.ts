import type { ApprovalRequest } from "../../features/approvals/domain/entities/approval-request";

export function approvalResourceRoute(request: ApprovalRequest): string | null {
  if (request.kind === "LEAVE" || request.kind === "LEAVE_CANCELLATION") {
    const query = new URLSearchParams({ company: request.companyId });
    return `/leave/requests/${encodeURIComponent(request.resourceId)}?${query}`;
  }
  if (request.kind === "OVERTIME") {
    const query = new URLSearchParams({ company: request.companyId });
    return `/workforce/overtime/${encodeURIComponent(request.resourceId)}?${query}`;
  }
  if (request.kind === "EXPENSE") {
    const query = new URLSearchParams({ company: request.companyId });
    return `/expenses/submissions/${encodeURIComponent(request.resourceId)}?${query}`;
  }
  return null;
}
