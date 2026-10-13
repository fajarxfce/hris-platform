import type { HttpClient } from "../../../../core/data/http/http-client";
import type { ExpensePolicyChangeDto } from "../models/expense-policy-change-dto";
import {
  expenseCategoryPageDto,
  expensePolicyReviewDto,
  expenseReceiptDto,
} from "../models/expense-policy-dto";
import type { ExpensePolicyDataSource } from "./expense-policy-data-source";

export class HttpExpensePolicyDataSource implements ExpensePolicyDataSource {
  constructor(private readonly http: HttpClient) {}
  async save(
    company: string,
    id: string,
    operation: string,
    change: ExpensePolicyChangeDto,
    signal: AbortSignal,
  ) {
    return expenseReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/expenses/categories/${id}`,
          method: "PUT",
          operationId: operation,
          body: change,
        },
        signal,
      ),
    );
  }
  async list(company: string, active: boolean | null, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (active !== null) query.set("active", String(active));
    if (after !== null) query.set("after", after);
    return expenseCategoryPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/expenses/policies?${query}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, historyAfter: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ historyLimit: "20" });
    if (historyAfter !== null) query.set("historyAfter", historyAfter);
    return expensePolicyReviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/expenses/policies/${id}?${query}` },
        signal,
      ),
    );
  }
}
