import type { AccountId } from "../../../../core/domain/identifiers";
export type PlatformAccess = Readonly<{
  accountId: AccountId;
  permissions: readonly string[];
}>;
