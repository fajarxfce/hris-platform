import type { HttpClient } from "../../../../core/data/http/http-client";
import type { PayrollPolicyChangeDto } from "../models/payroll-policy-change-dto";
import {
  payrollIncomeTaxRulesDto,
  payrollPolicyReceiptDto,
  payrollPolicyReviewDto,
} from "../models/payroll-policy-dto";
import type { PayrollPolicyDataSource } from "./payroll-policy-data-source";

export class HttpPayrollPolicyDataSource implements PayrollPolicyDataSource {
  constructor(private readonly http: HttpClient) {}
  async review(company: string, month: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ asOf: month, historyLimit: "20" });
    if (after !== null) query.set("historyAfter", after);
    return payrollPolicyReviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/policy/review?${query}` },
        signal,
      ),
    );
  }
  async rules(company: string, signal: AbortSignal) {
    return payrollIncomeTaxRulesDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/income-tax-rules` },
        signal,
      ),
    );
  }
  async save(
    company: string,
    operation: string,
    change: PayrollPolicyChangeDto,
    signal: AbortSignal,
  ) {
    return payrollPolicyReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/policy`,
          method: "PUT",
          operationId: operation,
          body: change,
        },
        signal,
      ),
    );
  }
}
