import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollCostReportRepository } from "../../domain/repositories/payroll-cost-report-repository";
import type { PayrollCostReportDataSource } from "../datasources/payroll-cost-report-data-source";
import { toPayrollCostReport } from "../mappers/payroll-cost-report-mapper";

export class RemotePayrollCostReportRepository implements PayrollCostReportRepository {
  constructor(private readonly source: PayrollCostReportDataSource) {}
  load(company: CompanyId, from: string, until: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollCostReport(
        await this.source.load(company, from, until, signal),
        company,
        from,
        until,
      ),
    );
  }
}
