import type { PayrollRunComparisonDto } from "../models/payroll-run-comparison-dto";
export interface PayrollComparisonDataSource {
  compare(
    company: string,
    id: string,
    baseline: string,
    after: string | null,
    changedOnly: boolean,
    signal: AbortSignal,
  ): Promise<PayrollRunComparisonDto>;
}
