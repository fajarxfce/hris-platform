import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { AccountId, OperationId } from "../../../../core/domain/identifiers";
import type { AccountAccessChange } from "../../domain/entities/account-access-change";
import type { ManagedAccountRepository } from "../../domain/repositories/managed-account-repository";
import type { ManagedAccountDataSource } from "../datasources/managed-account-data-source";
import { toManagedAccountPage, toManagedAccountReview } from "../mappers/managed-account-mapper";
export class RemoteManagedAccountRepository implements ManagedAccountRepository {
  constructor(private readonly source: ManagedAccountDataSource) {}
  list(query: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toManagedAccountPage(await this.source.list(query, after, signal), after),
    );
  }
  get(id: AccountId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toManagedAccountReview(await this.source.get(id, signal), id),
    );
  }
  save(operation: OperationId, input: AccountAccessChange, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const { accountId, platformPermissions, ...fields } = input;
      const receipt = await this.source.save(
        accountId,
        operation,
        {
          ...fields,
          platformPermissions: [...platformPermissions],
        },
        signal,
      );
      if (receipt.id.toLowerCase() !== accountId || receipt.version !== input.expectedVersion + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
