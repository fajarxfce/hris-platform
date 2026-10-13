import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseDraftReferenceQuery } from "../../domain/entities/expense-draft-references";
import type { ExpenseDraftingRepository } from "../../domain/repositories/expense-drafting-repository";
import type { ExpenseDraftingDataSource } from "../datasources/expense-drafting-data-source";
import {
  toExpenseCostCenterPage,
  toExpenseDraftDirectory,
  toExpenseDraftReceiptPage,
  toExpenseDraftReferences,
} from "../mappers/expense-drafting-mapper";
export class RemoteExpenseDraftingRepository implements ExpenseDraftingRepository {
  constructor(private readonly source: ExpenseDraftingDataSource) {}
  employees(company: CompanyId, query: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toExpenseDraftDirectory(
        await this.source.employees(company, query, after, signal),
        company,
        after,
      ),
    );
  }
  receipts(company: CompanyId, employmentId: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toExpenseDraftReceiptPage(
        await this.source.receipts(company, employmentId, after, signal),
        company,
        employmentId,
        after,
      ),
    );
  }
  references(
    company: CompanyId,
    employmentId: string,
    query: ExpenseDraftReferenceQuery,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toExpenseDraftReferences(
        await this.source.references(
          company,
          employmentId,
          query.categoryIds,
          query.costCenterIds,
          query.receiptRevisionIds,
          signal,
        ),
        company,
        employmentId,
        query,
      ),
    );
  }
  costCenters(company: CompanyId, query: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toExpenseCostCenterPage(
        await this.source.costCenters(company, query, after, signal),
        company,
        after,
      ),
    );
  }
}
