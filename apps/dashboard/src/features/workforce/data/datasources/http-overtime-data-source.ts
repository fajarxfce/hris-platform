import type { HttpClient } from "../../../../core/data/http/http-client";
import { workforceEmployeePageDto } from "../models/calendar-dto";
import type { OvertimeActualDto } from "../models/overtime-actual-dto";
import type { OvertimeDecisionDto } from "../models/overtime-decision-dto";
import { overtimeDetailsDto } from "../models/overtime-details-dto";
import type { OvertimePlanDto } from "../models/overtime-plan-dto";
import {
  type OvertimePlanningSearchDto,
  overtimePlanningReviewDto,
} from "../models/overtime-planning-dto";
import { overtimeReceiptDto } from "../models/overtime-receipt-dto";
import { type OvertimeSearchDto, overtimePageDto } from "../models/overtime-request-dto";
import type { OvertimeWithdrawalDto } from "../models/overtime-withdrawal-dto";
import type { OvertimeDataSource } from "./overtime-data-source";

export class HttpOvertimeDataSource implements OvertimeDataSource {
  constructor(private readonly http: HttpClient) {}
  async planningEmployees(company: string, search: OvertimePlanningSearchDto, signal: AbortSignal) {
    const query = new URLSearchParams({
      workDate: search.workDate,
      query: search.query,
      limit: "50",
    });
    if (search.after !== null) query.set("after", search.after);
    return workforceEmployeePageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/overtime/planning/employees?${query}`,
        },
        signal,
      ),
    );
  }
  async planningReview(company: string, employeeId: string, workDate: string, signal: AbortSignal) {
    const query = new URLSearchParams({ workDate });
    return overtimePlanningReviewDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/overtime/planning/employees/${employeeId}?${query}`,
        },
        signal,
      ),
    );
  }
  async plan(company: string, operation: string, body: OvertimePlanDto, signal: AbortSignal) {
    return overtimeReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/overtime`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async submitActual(
    company: string,
    id: string,
    operation: string,
    body: OvertimeActualDto,
    signal: AbortSignal,
  ) {
    return overtimeReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/overtime/${id}/actual`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async decide(
    company: string,
    id: string,
    operation: string,
    body: OvertimeDecisionDto,
    signal: AbortSignal,
  ) {
    return overtimeReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/overtime/${id}/decisions`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async withdraw(
    company: string,
    id: string,
    operation: string,
    body: OvertimeWithdrawalDto,
    signal: AbortSignal,
  ) {
    return overtimeReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/workforce/overtime/${id}/withdraw`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async list(company: string, search: OvertimeSearchDto, signal: AbortSignal) {
    const query = new URLSearchParams({ from: search.from, until: search.until, limit: "50" });
    if (search.employeeId !== null) query.set("employeeId", search.employeeId);
    if (search.status !== null) query.set("status", search.status);
    if (search.after !== null) query.set("after", search.after);
    return overtimePageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/workforce/overtime?${query}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, historyAfter: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ historyLimit: "50" });
    if (historyAfter !== null) query.set("historyAfter", historyAfter);
    return overtimeDetailsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/workforce/overtime/${id}?${query}` },
        signal,
      ),
    );
  }
}
