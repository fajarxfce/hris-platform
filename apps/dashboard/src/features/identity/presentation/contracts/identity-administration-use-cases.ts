import type { LoadCompanyMember } from "../../domain/usecases/load-company-member";
import type { LoadCompanyMembers } from "../../domain/usecases/load-company-members";

export type IdentityAdministrationUseCases = Readonly<{
  loadCompanyMembers: Pick<LoadCompanyMembers, "execute">;
  loadCompanyMember: Pick<LoadCompanyMember, "execute">;
}>;
