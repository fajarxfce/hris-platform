import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { AccountId, CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { ApprovalDelegationId } from "../../domain/entities/approval-delegation";
import type { ApprovalDelegationChange } from "../../domain/entities/approval-delegation-change";
import type { ApprovalDelegationRepository } from "../../domain/repositories/approval-delegation-repository";
import type { ApprovalDelegationDataSource } from "../datasources/approval-delegation-data-source";
import {
  toApprovalDelegation,
  toApprovalDelegationPage,
} from "../mappers/approval-delegation-mapper";

export class RemoteApprovalDelegationRepository implements ApprovalDelegationRepository {
  constructor(private readonly source: ApprovalDelegationDataSource) {}
  list(company: CompanyId, account: AccountId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toApprovalDelegationPage(
        await this.source.list(company, after, signal),
        company,
        account,
        after,
      ),
    );
  }
  get(company: CompanyId, id: ApprovalDelegationId, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const delegation = toApprovalDelegation(await this.source.get(company, id, signal), company);
      if (delegation.id !== id) throw new InvalidHttpResponseError();
      return delegation;
    });
  }
  save(
    company: CompanyId,
    operation: OperationId,
    change: ApprovalDelegationChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const id = change.id;
      const receipt = await this.source.save(
        company,
        id,
        operation,
        {
          kind: change.kind,
          fromAccount: change.fromAccount,
          toAccount: change.toAccount,
          validFrom: change.validFrom,
          validUntil: change.validUntil,
          active: change.active,
          expectedVersion: change.expectedVersion,
          reason: change.reason,
        },
        signal,
      );
      if (receipt.id.toLowerCase() !== id || receipt.version !== (change.expectedVersion ?? -1) + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
