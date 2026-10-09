export function canReadClientSettings(permissions: readonly string[]): boolean {
  return permissions.includes("settings.manage");
}

export function isClientPolicyVersion(value: string): boolean {
  return /^(0|[1-9][0-9]{0,3})$/u.test(value);
}
