import type { HttpClient } from "../../../../core/data/http/http-client";
import { workforceEmployeePageDto } from "../models/calendar-dto";
import {
  type WorkPeriodCommandDto,
  type WorkPeriodEmployeeSearchDto,
  workPeriodReceiptDto,
} from "../models/work-period-command-dto";
import { workPeriodOverviewDto } from "../models/work-period-overview-dto";
import { workPeriodSnapshotDto } from "../models/work-period-snapshot-dto";
import type { WorkPeriodDataSource } from "./work-period-data-source";

export class HttpWorkPeriodDataSource implements WorkPeriodDataSource {
  constructor(private readonly http: HttpClient) {}
  async overview(company: string, month: string, signal: AbortSignal) {
    return workPeriodOverviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/workforce/periods/${month}` },
        signal,
      ),
    );
  }
  async employees(company: string, search: WorkPeriodEmployeeSearchDto, signal: AbortSignal) {
    const query = new URLSearchParams({ query: search.query, limit: "50" });
    if (search.after !== null) query.set("after", search.after);
    return workforceEmployeePageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/periods/${search.month}/employees?${query}`,
        },
        signal,
      ),
    );
  }
  async snapshot(company: string, month: string, employee: string, signal: AbortSignal) {
    return workPeriodSnapshotDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/workforce/periods/${month}/employees/${employee}` },
        signal,
      ),
    );
  }
  async close(
    company: string,
    month: string,
    operation: string,
    body: WorkPeriodCommandDto,
    signal: AbortSignal,
  ) {
    return workPeriodReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/periods/${month}/close`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async recover(
    company: string,
    month: string,
    operation: string,
    body: WorkPeriodCommandDto,
    signal: AbortSignal,
  ) {
    return workPeriodReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/periods/${month}/recover`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
}
