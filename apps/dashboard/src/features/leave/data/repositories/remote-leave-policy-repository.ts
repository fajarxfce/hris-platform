import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { LeavePolicyChange } from "../../domain/entities/leave-policy-change";
import type { LeavePolicyId } from "../../domain/entities/leave-policy-definition";
import type { LeavePolicyRepository } from "../../domain/repositories/leave-policy-repository";
import type { LeavePolicyDataSource } from "../datasources/leave-policy-data-source";
import { toLeavePolicyPage, toLeavePolicyReview } from "../mappers/leave-policy-definition-mapper";

export class RemoteLeavePolicyRepository implements LeavePolicyRepository {
  constructor(private readonly source: LeavePolicyDataSource) {}
  save(company: CompanyId, operation: OperationId, change: LeavePolicyChange, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.save(
        company,
        change.id,
        operation,
        {
          code: change.code,
          name: change.name,
          effectiveFrom: change.effectiveFrom,
          paid: change.paid,
          allowPartialDays: change.allowPartialDays,
          minServiceMonths: change.minServiceMonths,
          allowedContracts: [...change.allowedContracts],
          maxRequestDays: change.maxRequestDays,
          active: change.active,
          expectedVersion: change.expectedVersion,
          reason: change.reason,
          attachmentRequired: change.attachmentRequired,
          accrual: change.accrual ? { ...change.accrual } : null,
        },
        signal,
      );
      if (
        receipt.id.toLowerCase() !== change.id ||
        receipt.version !== (change.expectedVersion ?? -1) + 1
      )
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
  list(company: CompanyId, active: boolean | null, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeavePolicyPage(
        await this.source.list(company, active, after, signal),
        company,
        active,
        after,
      ),
    );
  }
  get(company: CompanyId, id: LeavePolicyId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeavePolicyReview(await this.source.get(company, id, after, signal), company, id, after),
    );
  }
}
