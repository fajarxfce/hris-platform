import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { ExpenseCategoryPage } from "../entities/expense-category";
import type { ExpensePolicyChange } from "../entities/expense-policy-change";
import type { ExpensePolicyId, ExpensePolicyPage } from "../entities/expense-policy-definition";
import type { ExpensePolicyReview } from "../entities/expense-policy-review";

export interface ExpensePolicyRepository {
  categories(
    company: CompanyId,
    asOf: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<ExpenseCategoryPage>>;
  save(
    company: CompanyId,
    operation: OperationId,
    change: ExpensePolicyChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  list(
    company: CompanyId,
    active: boolean | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<ExpensePolicyPage>>;
  get(
    company: CompanyId,
    id: ExpensePolicyId,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<Result<ExpensePolicyReview>>;
}
