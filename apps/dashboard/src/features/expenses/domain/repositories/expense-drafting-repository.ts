import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { ExpenseCostCenterPage } from "../entities/expense-cost-center";
import type { ExpenseDraftDirectory } from "../entities/expense-draft-employee";
import type { ExpenseDraftReceiptPage } from "../entities/expense-draft-receipt-option";
import type {
  ExpenseDraftReferenceQuery,
  ExpenseDraftReferences,
} from "../entities/expense-draft-references";
export interface ExpenseDraftingRepository {
  employees(
    company: CompanyId,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<ExpenseDraftDirectory>>;
  receipts(
    company: CompanyId,
    employmentId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<ExpenseDraftReceiptPage>>;
  references(
    company: CompanyId,
    employmentId: string,
    query: ExpenseDraftReferenceQuery,
    signal: AbortSignal,
  ): Promise<Result<ExpenseDraftReferences>>;
  costCenters(
    company: CompanyId,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<ExpenseCostCenterPage>>;
}
