import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type ShiftChangeDto,
  type ShiftSearchDto,
  shiftDto,
  shiftPageDto,
  shiftReceiptDto,
} from "../models/shift-dto";
import type { ShiftDataSource } from "./shift-data-source";

export class HttpShiftDataSource implements ShiftDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(company: string, search: ShiftSearchDto, signal: AbortSignal) {
    const query = new URLSearchParams({ query: search.query, limit: "50" });
    if (search.after !== null) query.set("after", search.after);
    return shiftPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/workforce/shifts?${query}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return shiftDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/workforce/shifts/${id}` },
        signal,
      ),
    );
  }
  async save(
    company: string,
    id: string,
    operation: string,
    body: ShiftChangeDto,
    signal: AbortSignal,
  ) {
    return shiftReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/shifts/${id}`,
          method: "PUT",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
}
