import type { CompensationChangeDto } from "../models/compensation-change-dto";
import type {
  CompensationEmployeesDto,
  CompensationReceiptDto,
  CompensationReviewDto,
} from "../models/compensation-dto";
export interface CompensationDataSource {
  employees(
    company: string,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<CompensationEmployeesDto>;
  review(
    company: string,
    employee: string,
    month: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<CompensationReviewDto>;
  save(
    company: string,
    employee: string,
    operation: string,
    input: CompensationChangeDto,
    signal: AbortSignal,
  ): Promise<CompensationReceiptDto>;
}
