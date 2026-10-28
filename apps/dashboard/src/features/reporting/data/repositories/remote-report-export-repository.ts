import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import { binaryFilePartBytes } from "../../../../core/domain/files/binary-file";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type {
  ChangeReportExport,
  ReportExportCursor,
  ReportExportDetails,
  StartReportExport,
} from "../../domain/entities/report-export";
import type { ReportExportRepository } from "../../domain/repositories/report-export-repository";
import type { ReportExportDataSource } from "../datasources/report-export-data-source";
import { toReportExport, toReportExportHistory } from "../mappers/report-export-mapper";
export class RemoteReportExportRepository implements ReportExportRepository {
  constructor(private readonly source: ReportExportDataSource) {}
  list(company: CompanyId, before: ReportExportCursor | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toReportExportHistory(await this.source.list(company, before, signal), company, before),
    );
  }
  get(company: CompanyId, id: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const value = toReportExport(await this.source.get(company, id, signal), company);
      if (value.id !== id) throw new InvalidHttpResponseError();
      return value;
    });
  }
  start(company: CompanyId, operation: OperationId, input: StartReportExport, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.start(
        company,
        operation,
        {
          ...input,
          parameters: { ...input.parameters, companies: [...input.parameters.companies] },
        },
        signal,
      );
      if (receipt.version !== 0 || receipt.id.toLowerCase() === operation)
        throw new InvalidHttpResponseError();
      return Object.freeze({ ...receipt, id: receipt.id.toLowerCase() });
    });
  }
  change(
    company: CompanyId,
    operation: OperationId,
    kind: "cancel" | "resume",
    input: ChangeReportExport,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.change(
        company,
        input.id,
        operation,
        kind,
        input.expectedVersion,
        signal,
      );
      if (receipt.id.toLowerCase() !== input.id || receipt.version !== input.expectedVersion + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ ...receipt, id: input.id });
    });
  }
  async content(company: CompanyId, value: ReportExportDetails, signal: AbortSignal) {
    const owned: ArrayBuffer[] = [];
    try {
      const result = await safeHttpCall(signal, async () => {
        const raw = await this.source.content(company, value.id, value.bytes, signal);
        owned.push(...raw.parts);
        if (
          raw.byteLength !== value.bytes ||
          raw.mediaType !== "text/csv" ||
          !new RegExp(`^"${value.id}-[0-9a-f]{64}"$`, "u").test(raw.etag) ||
          raw.parts.some((part) => part.byteLength < 1 || part.byteLength > binaryFilePartBytes) ||
          raw.parts.reduce((n, part) => n + part.byteLength, 0) !== value.bytes
        )
          throw new InvalidHttpResponseError();
        return Object.freeze({ byteLength: raw.byteLength, parts: Object.freeze([...raw.parts]) });
      });
      if (result.ok) owned.length = 0;
      return result;
    } finally {
      for (const part of owned) new Uint8Array(part).fill(0);
    }
  }
}
