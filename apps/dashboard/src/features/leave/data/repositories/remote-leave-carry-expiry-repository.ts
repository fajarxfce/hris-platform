import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { LeaveCarryExpiryId } from "../../domain/entities/leave-carry-expiry";
import type { LeaveCarryExpiryResume } from "../../domain/entities/leave-carry-expiry-resume";
import type { LeaveCarryExpiryRepository } from "../../domain/repositories/leave-carry-expiry-repository";
import type { LeaveCarryExpiryDataSource } from "../datasources/leave-carry-expiry-data-source";
import {
  toLeaveCarryExpiries,
  toLeaveCarryExpiryDetails,
  toLeaveCarryExpiryReceipt,
} from "../mappers/leave-carry-expiry-mapper";
export class RemoteLeaveCarryExpiryRepository implements LeaveCarryExpiryRepository {
  constructor(private readonly source: LeaveCarryExpiryDataSource) {}
  list(company: CompanyId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveCarryExpiries(await this.source.list(company, after, signal), company, after),
    );
  }
  get(company: CompanyId, id: LeaveCarryExpiryId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveCarryExpiryDetails(
        await this.source.get(company, id, after, signal),
        company,
        id,
        after,
      ),
    );
  }
  resume(operation: OperationId, input: LeaveCarryExpiryResume, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveCarryExpiryReceipt(
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
