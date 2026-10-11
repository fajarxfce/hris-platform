import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { ShiftChange } from "../../domain/entities/shift-change";
import type { ShiftId } from "../../domain/entities/shift-definition";
import type { ShiftSearch } from "../../domain/entities/shift-search";
import type { ShiftRepository } from "../../domain/repositories/shift-repository";
import type { ShiftDataSource } from "../datasources/shift-data-source";
import { toShiftChangeDto, toShiftReceipt } from "../mappers/shift-change-mapper";
import { toShift, toShiftPage } from "../mappers/shift-mapper";

export class RemoteShiftRepository implements ShiftRepository {
  constructor(private readonly source: ShiftDataSource) {}
  list(company: CompanyId, search: ShiftSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toShiftPage(await this.source.list(company, search, signal), company, search),
    );
  }
  get(company: CompanyId, id: ShiftId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toShift(await this.source.get(company, id, signal), company, id),
    );
  }
  save(company: CompanyId, operation: OperationId, change: ShiftChange, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toShiftReceipt(
        await this.source.save(company, change.id, operation, toShiftChangeDto(change), signal),
        change,
      ),
    );
  }
}
