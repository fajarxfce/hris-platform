import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { EmployeeImportId } from "../../domain/entities/employee-import";
import type {
  EmployeeImportApplication,
  EmployeeImportChange,
} from "../../domain/entities/employee-import-change";
import type { EmployeeImportRepository } from "../../domain/repositories/employee-import-repository";
import type { EmployeeImportDataSource } from "../datasources/employee-import-data-source";
import { toEmployeeImportReceipt } from "../mappers/employee-import-change-mapper";
import { toEmployeeImportPage, toEmployeeImportSummary } from "../mappers/employee-import-mapper";
import {
  toEmployeeImportAttempts,
  toEmployeeImportRows,
} from "../mappers/employee-import-results-mapper";

export class RemoteEmployeeImportRepository implements EmployeeImportRepository {
  apply(
    company: CompanyId,
    operation: OperationId,
    change: EmployeeImportApplication,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toEmployeeImportReceipt(
        await this.source.apply(
          company,
          change.importId,
          operation,
          {
            expectedVersion: change.expectedVersion,
            reason: change.reason,
            allowPartial: change.allowPartial,
          },
          signal,
        ),
        change,
      ),
    );
  }
  resume(
    company: CompanyId,
    operation: OperationId,
    change: EmployeeImportChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toEmployeeImportReceipt(
        await this.source.resume(
          company,
          change.importId,
          operation,
          { expectedVersion: change.expectedVersion, reason: change.reason },
          signal,
        ),
        change,
      ),
    );
  }
  cancel(
    company: CompanyId,
    operation: OperationId,
    change: EmployeeImportChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toEmployeeImportReceipt(
        await this.source.cancel(
          company,
          change.importId,
          operation,
          { expectedVersion: change.expectedVersion, reason: change.reason },
          signal,
        ),
        change,
      ),
    );
  }
  constructor(private readonly source: EmployeeImportDataSource) {}
  list(company: CompanyId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmployeeImportPage(await this.source.list(company, after, signal), company, after),
    );
  }
  summary(company: CompanyId, id: EmployeeImportId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmployeeImportSummary(await this.source.summary(company, id, signal), company, id),
    );
  }
  rows(company: CompanyId, id: EmployeeImportId, after: number | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmployeeImportRows(await this.source.rows(company, id, after, signal), after),
    );
  }
  attempts(company: CompanyId, id: EmployeeImportId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmployeeImportAttempts(await this.source.attempts(company, id, after, signal), after),
    );
  }
}
