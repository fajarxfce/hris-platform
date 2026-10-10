import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type ClientPolicyChangeDto,
  clientPolicyReceiptDto,
} from "../models/client-policy-change-dto";
import { clientPolicyRevisionDto } from "../models/client-policy-revision-dto";
import { clientPolicySettingsDto } from "../models/client-policy-settings-dto";
import type { ClientPolicyDataSource } from "./client-policy-data-source";

export class HttpClientPolicyDataSource implements ClientPolicyDataSource {
  constructor(private readonly http: HttpClient) {}

  async save(
    companyId: string,
    operation: string,
    input: ClientPolicyChangeDto,
    signal: AbortSignal,
  ) {
    return clientPolicyReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${companyId}/settings/client-policy`,
          method: "PUT",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }

  async settings(companyId: string, signal: AbortSignal) {
    return clientPolicySettingsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${companyId}/settings/client-policy` },
        signal,
      ),
    );
  }

  async revision(companyId: string, version: number, signal: AbortSignal) {
    return clientPolicyRevisionDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${companyId}/settings/client-policy/revisions/${version}` },
        signal,
      ),
    );
  }
}
