import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { PersonId } from "./person-profile";

export type PersonAccountBinding = Readonly<{
  personId: PersonId;
  ownerCompanyId: CompanyId;
  accountId: AccountId;
  expectedVersion: number;
  reason: string;
}>;
