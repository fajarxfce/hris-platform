import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type HolidayChangeDto,
  type HolidayRangeDto,
  holidayDto,
  holidayReceiptDto,
  holidaysDto,
} from "../models/holiday-dto";
import type { HolidayDataSource } from "./holiday-data-source";

export class HttpHolidayDataSource implements HolidayDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(company: string, range: HolidayRangeDto, signal: AbortSignal) {
    const query = new URLSearchParams({ from: range.from, until: range.until });
    return holidaysDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/workforce/holidays?${query}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return holidayDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/workforce/holidays/${id}` },
        signal,
      ),
    );
  }
  async save(
    company: string,
    id: string,
    operation: string,
    body: HolidayChangeDto,
    signal: AbortSignal,
  ) {
    return holidayReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/holidays/${id}`,
          method: "PUT",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
}
