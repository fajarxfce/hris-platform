import type { AccountId } from "../../../../core/domain/identifiers";
export type ManagedAccount = Readonly<{
  id: AccountId;
  email: string;
  displayName: string;
  active: boolean;
  invitationPending: boolean;
  mfaConfigured: boolean;
  platformPermissions: readonly string[];
  version: number;
}>;
