import type { HttpClient } from "../../../../core/data/http/http-client";
import { payrollAmendmentReceiptDto } from "../models/payroll-amendment-command-dto";
import {
  type PayrollAmendmentReviewDecisionDto,
  type PayrollAmendmentReviewSubmissionDto,
  type PayrollAmendmentReviewWithdrawalDto,
  payrollAmendmentReviewDetailsDto,
  payrollAmendmentReviewsDto,
} from "../models/payroll-amendment-review-dto";
import type { PayrollAmendmentReviewDataSource } from "./payroll-amendment-review-data-source";

export class HttpPayrollAmendmentReviewDataSource implements PayrollAmendmentReviewDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(company: string, amendment: string, signal: AbortSignal) {
    return payrollAmendmentReviewsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/amendments/${amendment}/reviews` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return payrollAmendmentReviewDetailsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/amendment-reviews/${id}` },
        signal,
      ),
    );
  }
  async submit(
    company: string,
    amendment: string,
    id: string,
    operation: string,
    input: PayrollAmendmentReviewSubmissionDto,
    signal: AbortSignal,
  ) {
    return payrollAmendmentReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/amendments/${amendment}/reviews/${id}`,
          method: "PUT",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async decide(
    company: string,
    amendment: string,
    id: string,
    operation: string,
    input: PayrollAmendmentReviewDecisionDto,
    signal: AbortSignal,
  ) {
    return payrollAmendmentReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/amendments/${amendment}/reviews/${id}/decisions`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async withdraw(
    company: string,
    amendment: string,
    id: string,
    operation: string,
    input: PayrollAmendmentReviewWithdrawalDto,
    signal: AbortSignal,
  ) {
    return payrollAmendmentReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/amendments/${amendment}/reviews/${id}/withdraw`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
