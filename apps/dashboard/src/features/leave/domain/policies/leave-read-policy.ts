export const canBrowseCompanyLeave = (permissions: readonly string[]): boolean =>
  permissions.includes("leave.read");
export const canBrowseEmployeeLeave = (permissions: readonly string[]): boolean =>
  permissions.some((permission) =>
    ["leave.read", "leave.team.read", "leave.self.manage"].includes(permission),
  );
