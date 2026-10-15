import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type ConfirmPayrollPaymentRecoveryDto,
  payrollPaymentRecoveryReviewDto,
} from "../models/payroll-payment-recovery-dto";
import { payrollPaymentTransitionReceiptDto } from "../models/payroll-payment-transition-dto";
import type { PayrollPaymentRecoveryDataSource } from "./payroll-payment-recovery-data-source";

export class HttpPayrollPaymentRecoveryDataSource implements PayrollPaymentRecoveryDataSource {
  constructor(private readonly http: HttpClient) {}
  async review(company: string, assessment: string, signal: AbortSignal) {
    return payrollPaymentRecoveryReviewDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/payslips/${assessment}/recoveries/review`,
        },
        signal,
      ),
    );
  }
  async confirm(
    company: string,
    assessment: string,
    id: string,
    operation: string,
    input: ConfirmPayrollPaymentRecoveryDto,
    signal: AbortSignal,
  ) {
    return payrollPaymentTransitionReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/payslips/${assessment}/recoveries/${id}`,
          method: "PUT",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
