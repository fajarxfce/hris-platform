import type { AccountId, CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { CompanyMemberGrant, CompanyMemberPage } from "../entities/company-member";
import type { CompanyMembershipChange } from "../entities/company-membership-change";

export interface CompanyMemberRepository {
  save(
    companyId: CompanyId,
    operation: OperationId,
    input: CompanyMembershipChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
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
