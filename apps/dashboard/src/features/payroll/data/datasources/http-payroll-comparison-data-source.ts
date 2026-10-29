import type { HttpClient } from "../../../../core/data/http/http-client";
import { payrollRunComparisonDto } from "../models/payroll-run-comparison-dto";
import type { PayrollComparisonDataSource } from "./payroll-comparison-data-source";
export class HttpPayrollComparisonDataSource implements PayrollComparisonDataSource {
  constructor(private readonly http: HttpClient) {}
  async compare(
    company: string,
    id: string,
    baseline: string,
    after: string | null,
    changedOnly: boolean,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({
      baselineId: baseline,
      changedOnly: String(changedOnly),
      limit: "50",
    });
    if (after) query.set("after", after);
    return payrollRunComparisonDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/runs/${id}/comparison?${query}` },
        signal,
      ),
    );
  }
}
