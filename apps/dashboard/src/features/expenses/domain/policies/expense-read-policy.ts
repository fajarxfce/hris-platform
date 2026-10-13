export const canManageExpensePolicies = (permissions: readonly string[]): boolean =>
  permissions.includes("expenses.policy.manage");
