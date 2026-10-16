import type { ManagedAccount } from "./managed-account";
export type ManagedAccountReview = Readonly<{
  account: ManagedAccount;
  assignablePermissions: readonly string[];
}>;
