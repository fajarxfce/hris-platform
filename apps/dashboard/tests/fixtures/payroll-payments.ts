import type {
  PayrollPaymentPageDto,
  PayrollPaymentReviewDto,
} from "../../src/features/payroll/data/models/payroll-payment-dto";
import { payrollIdentifier } from "./payroll-payment-identifiers";
export function payrollPaymentRecord({
  company = 0,
  index = 1,
  settled = 0,
  count = 3,
  status = "RELEASED",
}: {
  company?: number;
  index?: number;
  settled?: number;
  count?: number;
  status?: "PREPARED" | "RELEASED" | "CANCELLED";
} = {}): PayrollPaymentReviewDto {
  const released = status === "RELEASED",
    version = released ? settled + 1 : status === "PREPARED" ? 0 : 1;
  const total = 15_000_075n * BigInt(count);
  const batchStatus = released && settled === count ? "CLOSED" : status;
  const record: PayrollPaymentReviewDto = {
    batch: {
      id: payrollIdentifier(80_000 + index, company),
      status: batchStatus,
      version,
      title: `${company === 0 ? "North" : "South"} payroll ${String(index).padStart(2, "0")}`,
      totalAmount: `${total / 100n}.${String(total % 100n).padStart(2, "0")}`,
      currency: "IDR",
      itemCount: count,
      createdBy: payrollIdentifier(501, company),
      createdAt: `2026-10-13T05:00:${String(59 - index).padStart(2, "0")}Z`,
      releasedBy: released ? payrollIdentifier(502, company) : null,
      releasedAt: released ? "2026-10-13T06:00:00Z" : null,
      items: Array.from({ length: count }, (_, offset) => {
        const paid = released && offset < settled,
          succeeded = offset % 2 === 0;
        return {
          id: payrollIdentifier(90_000 + index * 100 + offset, company),
          assessmentId: payrollIdentifier(110_000 + index * 100 + offset, company),
          employmentId: payrollIdentifier(120_000 + offset, company),
          employeeNumber: `EMP-${String(offset + 1).padStart(3, "0")}`,
          employeeName: `Finance Employee ${offset + 1}`,
          taxMonth: "2026-09",
          plannedPaymentDate: "2026-10-13",
          amount: "150000.75",
          currency: "IDR",
          destination: {
            bankCode: "014",
            accountNumber: `0012345678${String(offset).padStart(2, "0")}`,
            accountName: `Finance Employee ${offset + 1}`,
          },
          status: paid
            ? succeeded
              ? "SUCCEEDED"
              : "FAILED"
            : released
              ? "PENDING"
              : status === "PREPARED"
                ? "PREPARED"
                : "CANCELLED",
          version: paid ? 2 : status === "PREPARED" ? 0 : 1,
          batchVersion: paid ? offset + 2 : version === 0 ? 0 : 1,
          transactionReference: paid && succeeded ? `BANK-${index}-${offset + 1}` : null,
          occurredAt: paid ? "2026-10-13T06:01:00Z" : null,
        };
      }),
    },
    history: {
      items: Array.from({ length: version + 1 }, (_, entry) => ({
        version: entry,
        kind:
          entry === 0
            ? "PREPARED"
            : entry === 1
              ? status === "CANCELLED"
                ? "CANCELLED"
                : "RELEASED"
              : "RECONCILED",
        status: entry === 0 ? "PREPARED" : entry === version ? batchStatus : "RELEASED",
        actorId: payrollIdentifier(entry === 0 ? 501 : 502, company),
        reason:
          entry === 0
            ? "Verified bank destinations"
            : entry === 1
              ? "Finance release reviewed"
              : `Bank confirmation ${entry - 1}`,
        recordedAt:
          entry === 0
            ? "2026-10-13T05:01:00Z"
            : entry === 1
              ? "2026-10-13T06:00:00Z"
              : "2026-10-13T06:02:00Z",
      })),
      nextCursor: null,
    },
    results: [],
    availableActions:
      status === "PREPARED"
        ? ["RELEASE", "CANCEL"]
        : status === "CANCELLED" || settled === count
          ? []
          : settled === 0
            ? ["RECONCILE", "EXPORT"]
            : ["RECONCILE"],
  };
  record.results = record.batch.items
    .filter((item) => item.occurredAt !== null)
    .map((item) => ({
      batchVersion: item.batchVersion,
      actorId: payrollIdentifier(502, company),
      recordedAt: "2026-10-13T06:02:00Z",
      resultingStatus: item.batchVersion === version && settled === count ? "CLOSED" : "RELEASED",
      results: [
        {
          itemId: item.id,
          status: item.status === "SUCCEEDED" ? "SUCCEEDED" : "FAILED",
          transactionReference: item.transactionReference,
          occurredAt: item.occurredAt ?? "",
          reason:
            item.status === "SUCCEEDED"
              ? "Confirmed on bank statement"
              : "Bank confirmed no transfer",
        },
      ],
    }));
  return record;
}
export function payrollPaymentReviewPage(
  record: PayrollPaymentReviewDto,
  after: number | null = null,
): PayrollPaymentReviewDto {
  const remaining = record.history.items.filter((entry) => after === null || entry.version > after),
    items = remaining.slice(0, 20);
  return {
    ...record,
    history: { items, nextCursor: remaining.length > 20 ? String(items.at(-1)?.version) : null },
  };
}
export function payrollPaymentList(records: PayrollPaymentReviewDto[]): PayrollPaymentPageDto {
  const items = records.slice(0, 20).map(({ batch }) => ({
    id: batch.id,
    status: batch.status,
    version: batch.version,
    title: batch.title,
    totalAmount: batch.totalAmount,
    currency: batch.currency,
    itemCount: batch.itemCount,
    createdBy: batch.createdBy,
    createdAt: batch.createdAt,
    pendingCount: batch.items.filter((item) => item.status === "PENDING").length,
    succeededCount: batch.items.filter((item) => item.status === "SUCCEEDED").length,
    failedCount: batch.items.filter((item) => item.status === "FAILED").length,
  }));
  return { items, nextCursor: records.length > 20 ? (items.at(-1)?.id ?? null) : null };
}
export const payrollPaymentCsv =
  '"payment_reference","account_number","amount"\r\n"sample-reference","\'001234567800","150000.75"\r\n';
