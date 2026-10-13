import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  expenseCostCenterPageDto,
  expenseDraftDirectoryDto,
  expenseDraftReceiptPageDto,
  expenseDraftReferencesDto,
} from "../models/expense-drafting-dto";
import type { ExpenseDraftingDataSource } from "./expense-drafting-data-source";
export class HttpExpenseDraftingDataSource implements ExpenseDraftingDataSource {
  constructor(private readonly http: HttpClient) {}
  async employees(company: string, query: string, after: string | null, signal: AbortSignal) {
    const parameters = new URLSearchParams({ query, limit: "20" });
    if (after !== null) parameters.set("after", after);
    return expenseDraftDirectoryDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/expenses/drafting/employees?${parameters}` },
        signal,
      ),
    );
  }
  async receipts(company: string, employmentId: string, after: string | null, signal: AbortSignal) {
    const parameters = new URLSearchParams({ limit: "20" });
    if (after !== null) parameters.set("after", after);
    return expenseDraftReceiptPageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/expenses/drafting/employees/${employmentId}/receipts?${parameters}`,
        },
        signal,
      ),
    );
  }
  async references(
    company: string,
    employmentId: string,
    categories: readonly string[],
    costCenters: readonly string[],
    receipts: readonly string[],
    signal: AbortSignal,
  ) {
    const parameters = new URLSearchParams();
    for (const id of categories) parameters.append("categoryIds", id);
    for (const id of costCenters) parameters.append("costCenterIds", id);
    for (const id of receipts) parameters.append("receiptRevisionIds", id);
    return expenseDraftReferencesDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/expenses/drafting/employees/${employmentId}/references?${parameters}`,
        },
        signal,
      ),
    );
  }
  async costCenters(company: string, query: string, after: string | null, signal: AbortSignal) {
    const parameters = new URLSearchParams({ query, limit: "20" });
    if (after !== null) parameters.set("after", after);
    return expenseCostCenterPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/expenses/cost-centers?${parameters}` },
        signal,
      ),
    );
  }
}
