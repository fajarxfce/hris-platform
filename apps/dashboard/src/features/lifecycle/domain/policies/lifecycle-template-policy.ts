export const canReadLifecycle = (permissions: readonly string[]): boolean =>
  permissions.includes("people.lifecycle.read");
export const canManageLifecycle = (permissions: readonly string[]): boolean =>
  permissions.includes("people.lifecycle.manage");
export const isLifecycleTemplateCode = (code: string): boolean =>
  /^[A-Z0-9][A-Z0-9_-]{1,31}$/u.test(code);
export const isLifecycleTaskKey = (key: string): boolean => /^[a-z][a-z0-9_-]{0,47}$/u.test(key);
