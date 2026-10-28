import { binaryResponseDto } from "../../../../core/data/http/binary-response-dto";
import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type ReportExportCursorDto,
  reportExportDto,
  reportExportHistoryDto,
  reportExportReceiptDto,
  type StartReportExportDto,
} from "../models/report-export-dto";
import type { ReportExportDataSource } from "./report-export-data-source";

const path = (company: string) =>
  `/api/v1/companies/${encodeURIComponent(company)}/reports/exports`;
export class HttpReportExportDataSource implements ReportExportDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(company: string, before: ReportExportCursorDto | null, signal: AbortSignal) {
    const query = new URLSearchParams({ size: "20" });
    if (before) {
      query.set("beforeAt", before.createdAt);
      query.set("beforeId", before.id);
    }
    return reportExportHistoryDto.parse(
      await this.http.request({ path: `${path(company)}?${query}` }, signal),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return reportExportDto.parse(
      await this.http.request({ path: `${path(company)}/${encodeURIComponent(id)}` }, signal),
    );
  }
  async start(
    company: string,
    operation: string,
    input: StartReportExportDto,
    signal: AbortSignal,
  ) {
    return reportExportReceiptDto.parse(
      await this.http.request(
        { path: path(company), method: "POST", operationId: operation, body: input },
        signal,
      ),
    );
  }
  async change(
    company: string,
    id: string,
    operation: string,
    kind: "cancel" | "resume",
    expectedVersion: number,
    signal: AbortSignal,
  ) {
    return reportExportReceiptDto.parse(
      await this.http.request(
        {
          path: `${path(company)}/${encodeURIComponent(id)}/${kind}`,
          method: "POST",
          operationId: operation,
          body: { expectedVersion },
        },
        signal,
      ),
    );
  }
  async content(company: string, id: string, maximumBytes: number, signal: AbortSignal) {
    return binaryResponseDto.parse(
      await this.http.request(
        {
          path: `${path(company)}/${encodeURIComponent(id)}/content`,
          timeoutMilliseconds: 60000,
          response: { type: "binary", mediaType: "text/csv", maximumBytes },
        },
        signal,
      ),
    );
  }
}
