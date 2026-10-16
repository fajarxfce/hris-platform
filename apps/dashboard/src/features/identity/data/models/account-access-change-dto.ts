export type AccountAccessChangeDto = Readonly<{
  expectedVersion: number;
  active: boolean;
  platformPermissions: readonly string[];
  reason: string;
}>;
