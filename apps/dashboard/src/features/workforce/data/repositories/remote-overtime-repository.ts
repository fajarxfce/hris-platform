import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { OvertimeRequestId } from "../../domain/entities/overtime-request";
import type { OvertimeSearch } from "../../domain/entities/overtime-search";
import type { OvertimeRepository } from "../../domain/repositories/overtime-repository";
import type { OvertimeDataSource } from "../datasources/overtime-data-source";
import { toOvertimeDetails } from "../mappers/overtime-details-mapper";
import { toOvertimePage } from "../mappers/overtime-page-mapper";

export class RemoteOvertimeRepository implements OvertimeRepository {
  constructor(private readonly source: OvertimeDataSource) {}
  list(company: CompanyId, search: OvertimeSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toOvertimePage(await this.source.list(company, search, signal), company, search),
    );
  }
  get(company: CompanyId, id: OvertimeRequestId, historyAfter: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toOvertimeDetails(
        await this.source.get(company, id, historyAfter, signal),
        company,
        id,
        historyAfter,
      ),
    );
  }
}
