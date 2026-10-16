import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { AccountId, OperationId } from "../../../../core/domain/identifiers";
import type { OidcBindingId } from "../../domain/entities/oidc-binding";
import type { OidcBindingChange } from "../../domain/entities/oidc-binding-change";
import type { OidcBindingRepository } from "../../domain/repositories/oidc-binding-repository";
import type { OidcBindingDataSource } from "../datasources/oidc-binding-data-source";
import { toOidcBinding, toOidcBindingPage } from "../mappers/oidc-binding-mapper";

export class RemoteOidcBindingRepository implements OidcBindingRepository {
  constructor(private readonly source: OidcBindingDataSource) {}
  list(accountId: AccountId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toOidcBindingPage(await this.source.list(accountId, after, signal), accountId, after),
    );
  }
  get(accountId: AccountId, id: OidcBindingId, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const value = toOidcBinding(await this.source.get(accountId, id, signal), accountId);
      if (value.id !== id) throw new InvalidHttpResponseError();
      return value;
    });
  }
  save(operation: OperationId, input: OidcBindingChange, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const { accountId, id, ...fields } = input;
      const receipt = await this.source.save(accountId, id, operation, fields, signal);
      if (receipt.id.toLowerCase() !== id || receipt.version !== (input.expectedVersion ?? -1) + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
