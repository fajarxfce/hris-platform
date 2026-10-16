import type { AccountId } from "../../../../core/domain/identifiers";
export type AccountInvitation = Readonly<{
  accountId: AccountId;
  email: string;
  displayName: string;
  reason: string;
  expectedVersion: number | null;
}>;
