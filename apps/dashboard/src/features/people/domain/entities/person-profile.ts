import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";

export type PersonId = string & { readonly personId: unique symbol };
export type PersonProfileFields = Readonly<{
  legalName: string;
  birthDate: string | null;
  nationality: string;
  email: string | null;
}>;
export type PersonProfile = PersonProfileFields &
  Readonly<{
    personId: PersonId;
    ownerCompanyId: CompanyId;
    accountId: AccountId | null;
    version: number;
  }>;

export type PersonProfileChange = PersonProfileFields &
  Readonly<{
    personId: PersonId;
    ownerCompanyId: CompanyId;
    expectedVersion: number;
    reason: string;
  }>;
