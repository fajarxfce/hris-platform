import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { TaxOpening } from "../entities/tax-opening";
import type { TaxOpeningChange, TaxOpeningVerification } from "../entities/tax-opening-change";
import type {
  TaxOpeningEmployees,
  TaxOpeningReview,
  TaxOpeningSearch,
} from "../entities/tax-opening-review";
export interface TaxOpeningRepository {
  employees(
    company: CompanyId,
    search: TaxOpeningSearch,
    signal: AbortSignal,
  ): Promise<Result<TaxOpeningEmployees>>;
  review(
    company: CompanyId,
    employee: string,
    year: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<TaxOpeningReview>>;
  revision(
    company: CompanyId,
    employee: string,
    year: string,
    revision: number,
    signal: AbortSignal,
  ): Promise<Result<TaxOpening>>;
  save(
    company: CompanyId,
    employee: string,
    year: string,
    operation: OperationId,
    input: TaxOpeningChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  verify(
    company: CompanyId,
    employee: string,
    year: string,
    operation: OperationId,
    input: TaxOpeningVerification,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
