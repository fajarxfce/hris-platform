export const canReadApprovalInbox = (permissions: readonly string[]): boolean =>
  permissions.includes("approvals.read");

export const canManageApprovals = (permissions: readonly string[]): boolean =>
  permissions.includes("approvals.manage");
