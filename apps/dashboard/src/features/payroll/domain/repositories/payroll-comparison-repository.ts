import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { PayrollRunComparison } from "../entities/payroll-run-comparison";
export interface PayrollComparisonRepository {
  compare(
    company: CompanyId,
    id: string,
    baseline: string,
    after: string | null,
    changedOnly: boolean,
    signal: AbortSignal,
  ): Promise<Result<PayrollRunComparison>>;
}
