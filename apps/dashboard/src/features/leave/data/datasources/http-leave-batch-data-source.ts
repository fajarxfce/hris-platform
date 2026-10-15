import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type LeaveAccrualBatchDto,
  type LeaveBatchResumeDto,
  type LeaveBatchReviewQueryDto,
  type LeaveYearCloseBatchDto,
  leaveBatchDetailsDto,
  leaveBatchPageDto,
  leaveBatchReviewDto,
} from "../models/leave-batch-dto";
import { leaveReceiptDto } from "../models/leave-receipt-dto";
import { leaveTypePageDto } from "../models/leave-type-dto";
import type { LeaveBatchDataSource } from "./leave-batch-data-source";
export class HttpLeaveBatchDataSource implements LeaveBatchDataSource {
  constructor(private readonly http: HttpClient) {}
  async types(
    company: string,
    kind: "ACCRUAL" | "YEAR_CLOSE",
    after: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ kind, limit: "20" });
    if (after !== null) query.set("after", after);
    return leaveTypePageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/batches/types?${query}` },
        signal,
      ),
    );
  }
  async review(company: string, input: LeaveBatchReviewQueryDto, signal: AbortSignal) {
    const query = new URLSearchParams({
      kind: input.kind,
      typeId: input.typeId,
      period: input.period,
    });
    return leaveBatchReviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/batches/review?${query}` },
        signal,
      ),
    );
  }
  async list(company: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return leaveBatchPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/batches?${query}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return leaveBatchDetailsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/batches/${id}?${query}` },
        signal,
      ),
    );
  }
  async accrue(
    company: string,
    operation: string,
    input: LeaveAccrualBatchDto,
    signal: AbortSignal,
  ) {
    return leaveReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/leave/accrual-batches`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async closeYear(
    company: string,
    operation: string,
    input: LeaveYearCloseBatchDto,
    signal: AbortSignal,
  ) {
    return leaveReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/leave/year-close-batches`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async resume(
    company: string,
    id: string,
    operation: string,
    input: LeaveBatchResumeDto,
    signal: AbortSignal,
  ) {
    return leaveReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/leave/batches/${id}/resume`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
