import type { HttpClient } from "../../../../core/data/http/http-client";
import type { OidcBindingChangeDto } from "../models/oidc-binding-change-dto";
import {
  oidcBindingDto,
  oidcBindingPageDto,
  oidcBindingReceiptDto,
} from "../models/oidc-binding-dto";
import type { OidcBindingDataSource } from "./oidc-binding-data-source";

export class HttpOidcBindingDataSource implements OidcBindingDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(accountId: string, after: string | null, signal: AbortSignal) {
    const parameters = new URLSearchParams({ size: "50" });
    if (after !== null) parameters.set("after", after);
    return oidcBindingPageDto.parse(
      await this.http.request(
        { path: `/api/v1/identity/accounts/${accountId}/oidc?${parameters}` },
        signal,
      ),
    );
  }
  async get(accountId: string, id: string, signal: AbortSignal) {
    return oidcBindingDto.parse(
      await this.http.request(
        { path: `/api/v1/identity/accounts/${accountId}/oidc/${id}` },
        signal,
      ),
    );
  }
  async save(
    accountId: string,
    id: string,
    operation: string,
    input: OidcBindingChangeDto,
    signal: AbortSignal,
  ) {
    return oidcBindingReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/identity/accounts/${accountId}/oidc/${id}`,
          method: "PUT",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
