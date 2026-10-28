import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { PayrollCostReport } from "../entities/payroll-cost-report";

export interface PayrollCostReportRepository {
  load(
    company: CompanyId,
    from: string,
    until: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollCostReport>>;
}
