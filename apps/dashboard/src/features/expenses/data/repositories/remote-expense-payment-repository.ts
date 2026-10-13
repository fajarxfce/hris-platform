import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpensePaymentBatchId } from "../../domain/entities/expense-payment-batch";
import type { ExpensePaymentSearch } from "../../domain/entities/expense-payment-search";
import type { ExpensePaymentRepository } from "../../domain/repositories/expense-payment-repository";
import type { ExpensePaymentDataSource } from "../datasources/expense-payment-data-source";
import { toExpensePaymentPage } from "../mappers/expense-payment-mapper";
import { toExpensePaymentReview } from "../mappers/expense-payment-review-mapper";
export class RemoteExpensePaymentRepository implements ExpensePaymentRepository {
  constructor(private readonly source: ExpensePaymentDataSource) {}
  list(company: CompanyId, search: ExpensePaymentSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toExpensePaymentPage(
        await this.source.list(
          company,
          search.from,
          search.until,
          search.status,
          search.after,
          signal,
        ),
        company,
        search,
      ),
    );
  }
  review(company: CompanyId, id: ExpensePaymentBatchId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toExpensePaymentReview(
        await this.source.review(company, id, after, signal),
        company,
        id,
        after,
      ),
    );
  }
  worksheet(company: CompanyId, id: ExpensePaymentBatchId, signal: AbortSignal) {
    return safeHttpCall(signal, () => this.source.worksheet(company, id, signal));
  }
}
