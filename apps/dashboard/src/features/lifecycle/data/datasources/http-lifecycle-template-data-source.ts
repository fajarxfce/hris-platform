import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type LifecycleTemplateChangeDto,
  lifecycleTemplateReceiptDto,
} from "../models/lifecycle-template-change-dto";
import { lifecycleTemplateDto, lifecycleTemplatePageDto } from "../models/lifecycle-template-dto";
import type { LifecycleTemplateDataSource } from "./lifecycle-template-data-source";

export class HttpLifecycleTemplateDataSource implements LifecycleTemplateDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(company: string, after: string | null, signal: AbortSignal) {
    // Each template includes up to 64 tasks. Keep pages inside the shared 1 MiB response limit.
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return lifecycleTemplatePageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/lifecycle/templates?${query}`,
        },
        signal,
      ),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return lifecycleTemplateDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/lifecycle/templates/${id}`,
        },
        signal,
      ),
    );
  }
  async save(
    company: string,
    id: string,
    operation: string,
    change: LifecycleTemplateChangeDto,
    signal: AbortSignal,
  ) {
    return lifecycleTemplateReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/lifecycle/templates/${id}`,
          method: "PUT",
          operationId: operation,
          body: change,
        },
        signal,
      ),
    );
  }
}
