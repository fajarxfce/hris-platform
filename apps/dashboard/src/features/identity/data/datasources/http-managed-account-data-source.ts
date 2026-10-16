import type { HttpClient } from "../../../../core/data/http/http-client";
import type { AccountAccessChangeDto } from "../models/account-access-change-dto";
import {
  accountAccessReceiptDto,
  managedAccountPageDto,
  managedAccountReviewDto,
} from "../models/managed-account-dto";
import type { ManagedAccountDataSource } from "./managed-account-data-source";
export class HttpManagedAccountDataSource implements ManagedAccountDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(query: string, after: string | null, signal: AbortSignal) {
    const parameters = new URLSearchParams({ query, limit: "50" });
    if (after !== null) parameters.set("after", after);
    return managedAccountPageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/identity/accounts?${parameters}`,
        },
        signal,
      ),
    );
  }
  async get(id: string, signal: AbortSignal) {
    return managedAccountReviewDto.parse(
      await this.http.request(
        {
          path: `/api/v1/identity/accounts/${id}`,
        },
        signal,
      ),
    );
  }
  async save(id: string, operation: string, input: AccountAccessChangeDto, signal: AbortSignal) {
    return accountAccessReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/identity/accounts/${id}/access`,
          method: "PUT",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
