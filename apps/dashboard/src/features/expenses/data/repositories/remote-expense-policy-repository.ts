import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { ExpensePolicyChange } from "../../domain/entities/expense-policy-change";
import type { ExpensePolicyId } from "../../domain/entities/expense-policy-definition";
import type { ExpensePolicyRepository } from "../../domain/repositories/expense-policy-repository";
import type { ExpensePolicyDataSource } from "../datasources/expense-policy-data-source";
import { toExpenseCategoryPage } from "../mappers/expense-category-mapper";
import { toExpensePolicyPage, toExpensePolicyReview } from "../mappers/expense-policy-mapper";

export class RemoteExpensePolicyRepository implements ExpensePolicyRepository {
  constructor(private readonly source: ExpensePolicyDataSource) {}
  categories(company: CompanyId, asOf: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toExpenseCategoryPage(
        await this.source.categories(company, asOf, after, signal),
        company,
        asOf,
        after,
      ),
    );
  }
  save(
    company: CompanyId,
    operation: OperationId,
    change: ExpensePolicyChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.save(
        company,
        change.id,
        operation,
        {
          code: change.code,
          name: change.name,
          effectiveFrom: change.effectiveFrom,
          maximumLineAmount: change.maximumLineAmount,
          maximumClaimAmount: change.maximumClaimAmount,
          receiptRequired: change.receiptRequired,
          costCenterRequired: change.costCenterRequired,
          maximumAgeDays: change.maximumAgeDays,
          allowedContracts: [...change.allowedContracts],
          active: change.active,
          expectedVersion: change.expectedVersion,
          reason: change.reason,
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
      toExpensePolicyPage(
        await this.source.list(company, active, after, signal),
        company,
        active,
        after,
      ),
    );
  }
  get(company: CompanyId, id: ExpensePolicyId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toExpensePolicyReview(await this.source.get(company, id, after, signal), company, id, after),
    );
  }
}
