import type { HttpClient } from "../../../../core/data/http/http-client";
import { leaveEvidenceOptionsDto } from "../models/leave-evidence-option-dto";
import { leavePreparationDirectoryDto } from "../models/leave-preparation-employee-dto";
import { leaveRequestPreviewDto } from "../models/leave-request-preview-dto";
import type { LeaveRequestSelectionDto } from "../models/leave-request-submission-dto";
import { leaveTypePageDto } from "../models/leave-type-dto";
import type { LeavePreparationDataSource } from "./leave-preparation-data-source";

export class HttpLeavePreparationDataSource implements LeavePreparationDataSource {
  constructor(private readonly http: HttpClient) {}
  async types(company: string, asOf: string, after: string | null, signal: AbortSignal) {
    const parameters = new URLSearchParams({ asOf, limit: "20" });
    if (after !== null) parameters.set("after", after);
    return leaveTypePageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/types?${parameters}` },
        signal,
      ),
    );
  }
  async employees(company: string, query: string, after: string | null, signal: AbortSignal) {
    const parameters = new URLSearchParams({ query, limit: "20" });
    if (after !== null) parameters.set("after", after);
    return leavePreparationDirectoryDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/preparation/employees?${parameters}` },
        signal,
      ),
    );
  }
  async evidence(company: string, employee: string, after: string | null, signal: AbortSignal) {
    const parameters = new URLSearchParams({ limit: "20" });
    if (after !== null) parameters.set("after", after);
    return leaveEvidenceOptionsDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/leave/preparation/employees/${employee}/evidence?${parameters}`,
        },
        signal,
      ),
    );
  }
  async preview(company: string, body: LeaveRequestSelectionDto, signal: AbortSignal) {
    return leaveRequestPreviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/requests/preview`, method: "POST", body },
        signal,
      ),
    );
  }
}
