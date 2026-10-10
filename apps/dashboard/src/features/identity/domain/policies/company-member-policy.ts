export function canManageCompanyMembers(permissions: readonly string[]): boolean {
  return permissions.includes("identity.manage");
}
