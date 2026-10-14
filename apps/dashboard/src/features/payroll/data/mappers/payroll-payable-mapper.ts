import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { PayrollPayablePage } from "../../domain/entities/payroll-payable";
import type { PayrollPayableSearch } from "../../domain/entities/payroll-payable-search";
import { payrollPaymentAmountMinor } from "../../domain/policies/payroll-payment-amount";
import type { PayrollPayablePageDto } from "../models/payroll-payable-dto";
export function toPayrollPayablePage(
  raw: PayrollPayablePageDto,
  companyId: CompanyId,
  search: PayrollPayableSearch,
): PayrollPayablePage {
  let previous: { at: bigint; id: string } | null = null;
  const items = raw.items.map((row) => {
    const id = row.assessmentId.toLowerCase(),
      at = utcInstantMicroseconds(row.publishedAt);
    if (
      at === null ||
      id === search.after ||
      (payrollPaymentAmountMinor(row.amount) ?? 0n) <= 0n ||
      (previous && (at > previous.at || (at === previous.at && id >= previous.id)))
    )
      throw new InvalidHttpResponseError();
    previous = { at, id };
    return Object.freeze({
      ...row,
      companyId,
      assessmentId: id,
      employmentId: row.employmentId.toLowerCase(),
    });
  });
  const cursor = raw.nextCursor?.toLowerCase() ?? null;
  if (
    new Set(items.map((item) => item.assessmentId)).size !== items.length ||
    (cursor !== null &&
      (items.length !== 20 || cursor !== items.at(-1)?.assessmentId || cursor === search.after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: cursor });
}
