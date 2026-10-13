import type {
  ExpenseCostCenterPageDto,
  ExpenseDraftDirectoryDto,
  ExpenseDraftReceiptPageDto,
  ExpenseDraftReferencesDto,
} from "../models/expense-drafting-dto";
export interface ExpenseDraftingDataSource {
  employees(
    company: string,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<ExpenseDraftDirectoryDto>;
  receipts(
    company: string,
    employmentId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<ExpenseDraftReceiptPageDto>;
  references(
    company: string,
    employmentId: string,
    categories: readonly string[],
    costCenters: readonly string[],
    receipts: readonly string[],
    signal: AbortSignal,
  ): Promise<ExpenseDraftReferencesDto>;
  costCenters(
    company: string,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<ExpenseCostCenterPageDto>;
}
