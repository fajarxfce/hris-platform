import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type PersonProfileChangeDto,
  personProfileDto,
  personProfileHistoryDto,
  personProfileReceiptDto,
} from "../models/person-profile-dto";
import type { PersonProfileDataSource } from "./person-profile-data-source";

export class HttpPersonProfileDataSource implements PersonProfileDataSource {
  constructor(private readonly http: HttpClient) {}
  async get(companyId: string, employeeId: string, signal: AbortSignal) {
    return personProfileDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${companyId}/employees/${employeeId}/profile` },
        signal,
      ),
    );
  }
  async history(companyId: string, employeeId: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "50" });
    if (after !== null) query.set("after", after);
    return personProfileHistoryDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${companyId}/employees/${employeeId}/profile/history?${query}` },
        signal,
      ),
    );
  }
  async save(
    companyId: string,
    employeeId: string,
    operation: string,
    change: PersonProfileChangeDto,
    signal: AbortSignal,
  ) {
    return personProfileReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${companyId}/employees/${employeeId}/profile`,
          method: "PUT",
          operationId: operation,
          body: change,
        },
        signal,
      ),
    );
  }
}
