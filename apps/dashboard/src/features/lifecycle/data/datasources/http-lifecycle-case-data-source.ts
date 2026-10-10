import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  assignedLifecycleTaskPageDto,
  type LifecycleCaseQuery,
  lifecycleCaseDto,
  lifecycleCasePageDto,
} from "../models/lifecycle-case-dto";
import { lifecycleHistoryPageDto } from "../models/lifecycle-event-dto";
import {
  type LifecycleTaskChangeDto,
  lifecycleTaskReceiptDto,
} from "../models/lifecycle-task-change-dto";
import type { LifecycleCaseDataSource } from "./lifecycle-case-data-source";

export class HttpLifecycleCaseDataSource implements LifecycleCaseDataSource {
  constructor(private readonly http: HttpClient) {}
  async changeTask(
    company: string,
    id: string,
    key: string,
    operation: string,
    change: LifecycleTaskChangeDto,
    signal: AbortSignal,
  ) {
    return lifecycleTaskReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/lifecycle/cases/${id}/tasks/${key}`,
          method: "PUT",
          operationId: operation,
          body: change,
        },
        signal,
      ),
    );
  }
  async list(company: string, query: LifecycleCaseQuery, signal: AbortSignal) {
    // Cases include up to 64 tasks; keep the response within the shared 1 MiB transport budget.
    const parameters = new URLSearchParams({ limit: "10" });
    if (query.status !== null) parameters.set("status", query.status);
    if (query.employmentId !== null) parameters.set("employmentId", query.employmentId);
    if (query.after !== null) parameters.set("after", query.after);
    return lifecycleCasePageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/lifecycle/cases?${parameters}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return lifecycleCaseDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/lifecycle/cases/${id}` },
        signal,
      ),
    );
  }
  async history(company: string, id: string, after: number | null, signal: AbortSignal) {
    const parameters = new URLSearchParams({ limit: "50" });
    if (after !== null) parameters.set("after", String(after));
    return lifecycleHistoryPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/lifecycle/cases/${id}/history?${parameters}` },
        signal,
      ),
    );
  }
  async assigned(company: string, after: string | null, signal: AbortSignal) {
    const parameters = new URLSearchParams({ limit: "50" });
    if (after !== null) parameters.set("after", after);
    return assignedLifecycleTaskPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/lifecycle/tasks/assigned?${parameters}` },
        signal,
      ),
    );
  }
}
