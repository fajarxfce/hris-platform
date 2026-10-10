import type { AccountId } from "../../../../core/domain/identifiers";

export type CompanyMember = Readonly<{
  id: AccountId;
  email: string;
  displayName: string;
  accountActive: boolean;
  membershipActive: boolean;
  permissions: readonly string[];
  version: number;
}>;
export type CompanyMemberPage = Readonly<{
  items: readonly CompanyMember[];
  nextCursor: string | null;
}>;
export type CompanyMemberGrant = Readonly<{
  member: CompanyMember;
  directPermissions: readonly string[];
  roleTemplates: readonly Readonly<{
    id: string;
    code: string;
    name: string;
    permissions: readonly string[];
    version: number;
  }>[];
}>;
