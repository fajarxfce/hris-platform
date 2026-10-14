import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { CompensationChange } from "../entities/compensation-change";
import type { CompensationEmployees, CompensationReview } from "../entities/compensation-review";
export interface CompensationRepository {
  employees(
    company: CompanyId,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<CompensationEmployees>>;
  review(
    company: CompanyId,
    employee: string,
    month: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<CompensationReview>>;
  save(
    company: CompanyId,
    employee: string,
    operation: OperationId,
    input: CompensationChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
