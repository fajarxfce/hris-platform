import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type {
  DeleteReportFilterChange,
  SaveReportFilterChange,
} from "../../domain/entities/saved-report-filter";
import type { SavedReportFilterRepository } from "../../domain/repositories/saved-report-filter-repository";
import type { SavedReportFilterDataSource } from "../datasources/saved-report-filter-data-source";
import {
  toReportFilterReceipt,
  toSavedReportFilter,
  toSavedReportFilters,
} from "../mappers/saved-report-filter-mapper";
export class RemoteSavedReportFilterRepository implements SavedReportFilterRepository {
  constructor(private readonly source: SavedReportFilterDataSource) {}
  list(company: CompanyId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toSavedReportFilters(await this.source.list(company, signal), company),
    );
  }
  get(company: CompanyId, id: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const filter = toSavedReportFilter(await this.source.get(company, id, signal), company);
      if (filter.id !== id) throw new InvalidHttpResponseError();
      return filter;
    });
  }
  save(
    company: CompanyId,
    operation: OperationId,
    change: SaveReportFilterChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const { id, name, kind, definitionVersion, parameters, expectedVersion } = change;
      const raw = await this.source.save(
        company,
        id,
        operation,
        {
          name,
          kind,
          definitionVersion,
          expectedVersion,
          parameters: { ...parameters, companies: [...parameters.companies] },
        },
        signal,
      );
      return toReportFilterReceipt(raw, id, expectedVersion, false);
    });
  }
  delete(
    company: CompanyId,
    operation: OperationId,
    change: DeleteReportFilterChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toReportFilterReceipt(
        await this.source.delete(company, change.id, operation, change.expectedVersion, signal),
        change.id,
        change.expectedVersion,
        true,
      ),
    );
  }
}
