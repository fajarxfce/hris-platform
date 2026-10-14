import type { HttpClient } from "../../../../core/data/http/http-client";
import { payrollPayablePageDto } from "../models/payroll-payable-dto";
import {
  payrollPaymentPageDto,
  payrollPaymentReviewDto,
  payrollPaymentWorksheetDto,
  payrollPaymentWorksheetMaximumBytes,
} from "../models/payroll-payment-dto";
import type { PayrollPaymentPreparationDto } from "../models/payroll-payment-preparation-dto";
import type { PayrollPaymentReconciliationDto } from "../models/payroll-payment-reconciliation-dto";
import {
  type PayrollPaymentTransitionDto,
  payrollPaymentTransitionReceiptDto,
} from "../models/payroll-payment-transition-dto";
import type { PayrollPaymentDataSource } from "./payroll-payment-data-source";
export class HttpPayrollPaymentDataSource implements PayrollPaymentDataSource {
  constructor(private readonly http: HttpClient) {}
  async payables(
    company: string,
    from: string,
    until: string,
    after: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ from, until, limit: "20" });
    if (after !== null) query.set("after", after);
    return payrollPayablePageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/payments/payables?${query}` },
        signal,
      ),
    );
  }
  async prepare(
    company: string,
    id: string,
    operation: string,
    command: PayrollPaymentPreparationDto,
    signal: AbortSignal,
  ) {
    return payrollPaymentTransitionReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/payments/${id}`,
          method: "PUT",
          operationId: operation,
          body: command,
        },
        signal,
      ),
    );
  }
  async reconcile(
    company: string,
    id: string,
    operation: string,
    command: PayrollPaymentReconciliationDto,
    signal: AbortSignal,
  ) {
    return payrollPaymentTransitionReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/payments/${id}/reconcile`,
          method: "POST",
          operationId: operation,
          body: command,
        },
        signal,
      ),
    );
  }
  async transition(
    company: string,
    id: string,
    operation: string,
    action: "release" | "cancel",
    command: PayrollPaymentTransitionDto,
    signal: AbortSignal,
  ) {
    return payrollPaymentTransitionReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/payments/${id}/${action}`,
          method: "POST",
          operationId: operation,
          body: command,
        },
        signal,
      ),
    );
  }
  async list(
    company: string,
    from: string,
    until: string,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ from, until, limit: "20" });
    if (status !== null) query.set("status", status);
    if (after !== null) query.set("after", after);
    return payrollPaymentPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/payments?${query}` },
        signal,
      ),
    );
  }
  async review(company: string, id: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ historyLimit: "20" });
    if (after !== null) query.set("historyAfter", after);
    return payrollPaymentReviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/payments/${id}/review?${query}` },
        signal,
      ),
    );
  }
  async worksheet(company: string, id: string, signal: AbortSignal) {
    return payrollPaymentWorksheetDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/payments/${id}/export`,
          response: {
            type: "text",
            mediaType: "text/csv",
            maximumBytes: payrollPaymentWorksheetMaximumBytes,
          },
        },
        signal,
      ),
    );
  }
}
