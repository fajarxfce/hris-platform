import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ApprovalId } from "../../domain/entities/approval-request";
import type { ApprovalHistoryRepository } from "../../domain/repositories/approval-history-repository";
import type { ApprovalHistoryDataSource } from "../datasources/approval-history-data-source";
import { toApprovalHistory } from "../mappers/approval-history-mapper";
export class RemoteApprovalHistoryRepository implements ApprovalHistoryRepository {
  constructor(private readonly source: ApprovalHistoryDataSource) {}
  get(
    company: CompanyId,
    id: ApprovalId,
    after: number | null,
    expectedVersion: number | null,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toApprovalHistory(
        await this.source.get(company, id, after, expectedVersion, signal),
        company,
        id,
        after,
        expectedVersion,
      ),
    );
  }
}
