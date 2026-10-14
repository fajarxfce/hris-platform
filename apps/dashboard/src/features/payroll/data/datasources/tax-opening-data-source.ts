import type {
  TaxOpeningChangeDto,
  TaxOpeningDto,
  TaxOpeningEmployeesDto,
  TaxOpeningReceiptDto,
  TaxOpeningReviewDto,
  TaxOpeningVerificationDto,
} from "../models/tax-opening-dto";
export interface TaxOpeningDataSource {
  employees(
    company: string,
    year: string,
    query: string,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<TaxOpeningEmployeesDto>;
  review(
    company: string,
    employee: string,
    year: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<TaxOpeningReviewDto>;
  revision(
    company: string,
    employee: string,
    year: string,
    revision: number,
    signal: AbortSignal,
  ): Promise<TaxOpeningDto>;
  save(
    company: string,
    employee: string,
    year: string,
    operation: string,
    input: TaxOpeningChangeDto,
    signal: AbortSignal,
  ): Promise<TaxOpeningReceiptDto>;
  verify(
    company: string,
    employee: string,
    year: string,
    operation: string,
    input: TaxOpeningVerificationDto,
    signal: AbortSignal,
  ): Promise<TaxOpeningReceiptDto>;
}
