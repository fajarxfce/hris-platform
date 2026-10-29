export type PlatformAccountAccessAudit = Readonly<{
  id: string;
  accountId: string;
  active: boolean;
  permissions: readonly string[];
  reason: string | null;
}>;
