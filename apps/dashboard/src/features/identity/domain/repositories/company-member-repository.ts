import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { CompanyMemberGrant, CompanyMemberPage } from "../entities/company-member";

export interface CompanyMemberRepository {
  list(
    companyId: CompanyId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<CompanyMemberPage>>;
  get(
    companyId: CompanyId,
    accountId: AccountId,
    signal: AbortSignal,
  ): Promise<Result<CompanyMemberGrant>>;
}
