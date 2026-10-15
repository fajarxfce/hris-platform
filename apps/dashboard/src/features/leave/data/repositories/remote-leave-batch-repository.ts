import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { LeaveBatchId, LeaveBatchKind } from "../../domain/entities/leave-batch";
import type {
  LeaveBatchCreation,
  LeaveBatchResume,
} from "../../domain/entities/leave-batch-command";
import type { LeaveBatchSelection } from "../../domain/entities/leave-batch-review";
import type { LeaveBatchRepository } from "../../domain/repositories/leave-batch-repository";
import type { LeaveBatchDataSource } from "../datasources/leave-batch-data-source";
import {
  toLeaveBatchDetails,
  toLeaveBatches,
  toLeaveBatchReceipt,
  toLeaveBatchReview,
} from "../mappers/leave-batch-mapper";
import { toLeavePolicyPage } from "../mappers/leave-policy-definition-mapper";
export class RemoteLeaveBatchRepository implements LeaveBatchRepository {
  constructor(private readonly source: LeaveBatchDataSource) {}
  types(company: CompanyId, kind: LeaveBatchKind, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeavePolicyPage(
        await this.source.types(company, kind, after, signal),
        company,
        null,
        after,
      ),
    );
  }
  review(company: CompanyId, selection: LeaveBatchSelection, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveBatchReview(await this.source.review(company, selection, signal), company, selection),
    );
  }
  list(company: CompanyId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveBatches(await this.source.list(company, after, signal), company, after),
    );
  }
  get(company: CompanyId, id: LeaveBatchId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveBatchDetails(await this.source.get(company, id, after, signal), company, id, after),
    );
  }
  start(operation: OperationId, input: LeaveBatchCreation, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const fields = {
        id: input.id,
        typeId: input.typeId,
        expectedPolicyVersion: input.expectedPolicyVersion,
        employeeIds: input.employeeIds,
        reason: input.reason,
      };
      const receipt =
        input.kind === "ACCRUAL"
          ? await this.source.accrue(
              input.companyId,
              operation,
              { ...fields, month: input.period },
              signal,
            )
          : await this.source.closeYear(
              input.companyId,
              operation,
              { ...fields, year: Number(input.period.slice(0, 4)) },
              signal,
            );
      return toLeaveBatchReceipt(receipt, input.id, 0);
    });
  }
  resume(operation: OperationId, input: LeaveBatchResume, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveBatchReceipt(
        await this.source.resume(
          input.companyId,
          input.id,
          operation,
          { expectedVersion: input.expectedVersion, reason: input.reason },
          signal,
        ),
        input.id,
        input.expectedVersion + 1,
      ),
    );
  }
}
