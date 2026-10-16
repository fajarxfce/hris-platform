import type { AccountId } from "../../../../core/domain/identifiers";
export type AccountAccessChange = Readonly<{
  accountId: AccountId;
  expectedVersion: number;
  active: boolean;
  platformPermissions: readonly string[];
  reason: string;
}>;
