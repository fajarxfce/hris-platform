import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type PayrollReviewDecisionDto,
  type PayrollReviewSubmissionDto,
  type PayrollReviewWithdrawalDto,
  payrollReviewDetailsDto,
  payrollReviewPreparationDto,
  payrollReviewReceiptDto,
  payrollReviewsDto,
} from "../models/payroll-review-dto";
import type { PayrollReviewDataSource } from "./payroll-review-data-source";
export class HttpPayrollReviewDataSource implements PayrollReviewDataSource {
  constructor(private readonly http: HttpClient) {}
  async prepare(company: string, run: string, signal: AbortSignal) {
    return payrollReviewPreparationDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/runs/${run}/review-preparation` },
        signal,
      ),
    );
  }
  async list(company: string, run: string, signal: AbortSignal) {
    return payrollReviewsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/runs/${run}/reviews?limit=8` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return payrollReviewDetailsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/reviews/${id}` },
        signal,
      ),
    );
  }
  async submit(
    company: string,
    run: string,
    operation: string,
    input: PayrollReviewSubmissionDto,
    signal: AbortSignal,
  ) {
    return payrollReviewReceiptDto.parse(
      await this.http.request(
        {
          method: "POST",
          path: `/api/v1/companies/${company}/payroll/runs/${run}/reviews`,
          body: input,
          operationId: operation,
        },
        signal,
      ),
    );
  }
  async decide(
    company: string,
    id: string,
    operation: string,
    input: PayrollReviewDecisionDto,
    signal: AbortSignal,
  ) {
    return payrollReviewReceiptDto.parse(
      await this.http.request(
        {
          method: "POST",
          path: `/api/v1/companies/${company}/payroll/reviews/${id}/decisions`,
          body: input,
          operationId: operation,
        },
        signal,
      ),
    );
  }
  async withdraw(
    company: string,
    id: string,
    operation: string,
    input: PayrollReviewWithdrawalDto,
    signal: AbortSignal,
  ) {
    return payrollReviewReceiptDto.parse(
      await this.http.request(
        {
          method: "POST",
          path: `/api/v1/companies/${company}/payroll/reviews/${id}/withdraw`,
          body: input,
          operationId: operation,
        },
        signal,
      ),
    );
  }
}
