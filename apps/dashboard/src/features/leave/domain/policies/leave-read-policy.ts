export const canBrowseCompanyLeave = (permissions: readonly string[]): boolean =>
  permissions.includes("leave.read");
export const canManageLeavePolicies = (permissions: readonly string[]): boolean =>
  permissions.includes("leave.manage");
export const canBrowseEmployeeLeave = (permissions: readonly string[]): boolean =>
  permissions.some((permission) =>
    ["leave.read", "leave.team.read", "leave.self.manage"].includes(permission),
  );
