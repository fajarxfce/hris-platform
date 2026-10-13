import type { ExpensePolicyReviewDto } from "../../src/features/expenses/data/models/expense-policy-dto";

export const expensePolicyId = (company = 0, index = 1) =>
  `b810000${company}-0000-4000-8000-${String(index).padStart(12, "0")}`;
export function expensePolicyRecord(company = 0, index = 1, version = 2): ExpensePolicyReviewDto {
  const current = {
    id: expensePolicyId(company, index),
    code: `EXPENSE${String(index).padStart(3, "0")}`,
    name: `${company === 0 ? "North" : "South"} travel ${String(index).padStart(2, "0")}`,
    currency: "IDR" as const,
    effectiveFrom: "2027-01-01",
    maximumLineAmount: "500000.25",
    maximumClaimAmount: "2000000.75",
    receiptRequired: true,
    costCenterRequired: false,
    maximumAgeDays: 30,
    allowedContracts: ["PERMANENT" as const, "FIXED_TERM" as const],
    active: index % 2 === 0,
    version,
    appliedRevision: version,
  };
  return {
    current,
    history: {
      items: Array.from({ length: version + 1 }, (_, revision) => ({
        category: {
          ...current,
          appliedRevision: revision,
          allowedContracts: [...current.allowedContracts],
          effectiveFrom: revision === version ? current.effectiveFrom : "2026-01-01",
          maximumLineAmount: revision === version ? current.maximumLineAmount : "400000.50",
        },
        actorId: "b9000000-0000-4000-8000-000000000001",
        reason: `Policy review ${revision}`,
        recordedAt: "2026-10-13T08:00:00Z",
      })),
      nextCursor: null,
    },
  };
}
export function expensePolicyReviewPage(
  record: ExpensePolicyReviewDto,
  after: number | null = null,
): ExpensePolicyReviewDto {
  const rows = record.history.items.filter((item) => item.category.appliedRevision > (after ?? -1));
  const items = rows.slice(0, 20);
  return {
    current: record.current,
    history: {
      items,
      nextCursor: rows.length > 20 ? String(items.at(-1)?.category.appliedRevision) : null,
    },
  };
}
