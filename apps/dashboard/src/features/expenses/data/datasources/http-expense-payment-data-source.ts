import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  expensePaymentPageDto,
  expensePaymentReviewDto,
  expensePaymentWorksheetDto,
  expensePaymentWorksheetMaximumBytes,
} from "../models/expense-payment-dto";
import {
  type ExpensePaymentTransitionDto,
  expensePaymentTransitionReceiptDto,
} from "../models/expense-payment-transition-dto";
import type { ExpenseReconciliationDto } from "../models/expense-reconciliation-dto";
import type { ExpensePaymentDataSource } from "./expense-payment-data-source";
export class HttpExpensePaymentDataSource implements ExpensePaymentDataSource {
  constructor(private readonly http: HttpClient) {}
  async reconcile(
    company: string,
    id: string,
    operation: string,
    command: ExpenseReconciliationDto,
    signal: AbortSignal,
  ) {
    return expensePaymentTransitionReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/expenses/payments/${id}/reconcile`,
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
    command: ExpensePaymentTransitionDto,
    signal: AbortSignal,
  ) {
    return expensePaymentTransitionReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/expenses/payments/${id}/${action}`,
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
    return expensePaymentPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/expenses/payments?${query}` },
        signal,
      ),
    );
  }
  async review(company: string, id: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ historyLimit: "20" });
    if (after !== null) query.set("historyAfter", after);
    return expensePaymentReviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/expenses/payments/${id}/review?${query}` },
        signal,
      ),
    );
  }
  async worksheet(company: string, id: string, signal: AbortSignal) {
    return expensePaymentWorksheetDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/expenses/payments/${id}/export`,
          response: {
            type: "text",
            mediaType: "text/csv",
            maximumBytes: expensePaymentWorksheetMaximumBytes,
          },
        },
        signal,
      ),
    );
  }
}
