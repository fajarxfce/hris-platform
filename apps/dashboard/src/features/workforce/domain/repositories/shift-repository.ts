import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { ShiftChange } from "../entities/shift-change";
import type { ShiftDefinition, ShiftId } from "../entities/shift-definition";
import type { ShiftPage } from "../entities/shift-page";
import type { ShiftSearch } from "../entities/shift-search";

export interface ShiftRepository {
  list(company: CompanyId, search: ShiftSearch, signal: AbortSignal): Promise<Result<ShiftPage>>;
  get(company: CompanyId, id: ShiftId, signal: AbortSignal): Promise<Result<ShiftDefinition>>;
  save(
    company: CompanyId,
    operation: OperationId,
    change: ShiftChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
