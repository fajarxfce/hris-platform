import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { OvertimeActualSubmission } from "../../domain/entities/overtime-actual-submission";
import type { OvertimeDecision } from "../../domain/entities/overtime-decision";
import type { OvertimeRequestId } from "../../domain/entities/overtime-request";
import type { OvertimeRequestChange } from "../../domain/entities/overtime-request-change";
import type { OvertimeSearch } from "../../domain/entities/overtime-search";
import type { OvertimeRepository } from "../../domain/repositories/overtime-repository";
import type { OvertimeDataSource } from "../datasources/overtime-data-source";
import {
  toOvertimeActualDto,
  toOvertimeDecisionDto,
  toOvertimeReceipt,
  toOvertimeWithdrawalDto,
} from "../mappers/overtime-action-mapper";
import { toOvertimeDetails } from "../mappers/overtime-details-mapper";
import { toOvertimePage } from "../mappers/overtime-page-mapper";

export class RemoteOvertimeRepository implements OvertimeRepository {
  constructor(private readonly source: OvertimeDataSource) {}
  submitActual(
    company: CompanyId,
    operation: OperationId,
    change: OvertimeActualSubmission,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toOvertimeReceipt(
        await this.source.submitActual(
          company,
          change.id,
          operation,
          toOvertimeActualDto(change),
          signal,
        ),
        change,
      ),
    );
  }
  decide(
    company: CompanyId,
    operation: OperationId,
    change: OvertimeDecision,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toOvertimeReceipt(
        await this.source.decide(
          company,
          change.id,
          operation,
          toOvertimeDecisionDto(change),
          signal,
        ),
        change,
      ),
    );
  }
  withdraw(
    company: CompanyId,
    operation: OperationId,
    change: OvertimeRequestChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toOvertimeReceipt(
        await this.source.withdraw(
          company,
          change.id,
          operation,
          toOvertimeWithdrawalDto(change),
          signal,
        ),
        change,
      ),
    );
  }
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
