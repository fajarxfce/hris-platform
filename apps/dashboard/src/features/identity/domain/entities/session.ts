import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";

export type CompanyMembership = Readonly<{
  id: CompanyId;
  code: string;
  name: string;
  timezone: string;
}>;

export type Session = Readonly<{
  account: Readonly<{
    id: AccountId;
    email: string;
    displayName: string;
    mfaConfigured: boolean;
  }>;
  companies: readonly CompanyMembership[];
  permissions: readonly string[];
  assurance: Readonly<{
    required: boolean;
    verified: boolean;
    setupAvailable: boolean;
    validUntil: string | null;
    recentUntil: string | null;
  }>;
}>;

export type CompanyAccess = Readonly<{ companyId: CompanyId; permissions: readonly string[] }>;
