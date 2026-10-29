import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollComparisonRepository } from "../../domain/repositories/payroll-comparison-repository";
import type { PayrollComparisonDataSource } from "../datasources/payroll-comparison-data-source";
import { toPayrollRunComparison } from "../mappers/payroll-run-comparison-mapper";
export class RemotePayrollComparisonRepository implements PayrollComparisonRepository {
  constructor(private readonly source: PayrollComparisonDataSource) {}
  compare(
    company: CompanyId,
    id: string,
    baseline: string,
    after: string | null,
    changedOnly: boolean,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toPayrollRunComparison(
        await this.source.compare(company, id, baseline, after, changedOnly, signal),
        id,
        baseline,
        after,
        changedOnly,
      ),
    );
  }
}
