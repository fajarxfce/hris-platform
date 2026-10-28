import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type {
  DeleteReportFilterChange,
  SavedReportFilter,
  SaveReportFilterChange,
} from "../entities/saved-report-filter";

export interface SavedReportFilterRepository {
  list(company: CompanyId, signal: AbortSignal): Promise<Result<readonly SavedReportFilter[]>>;
  get(company: CompanyId, id: string, signal: AbortSignal): Promise<Result<SavedReportFilter>>;
  save(
    company: CompanyId,
    operation: OperationId,
    change: SaveReportFilterChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  delete(
    company: CompanyId,
    operation: OperationId,
    change: DeleteReportFilterChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
