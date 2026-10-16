import type { AccountId } from "../../../../core/domain/identifiers";

export type CompanyMembershipCandidate = Readonly<{
  accountId: AccountId;
  displayName: string;
  email: string;
  assignablePermissions: readonly string[];
}>;
