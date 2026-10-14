import type {
  PayrollPolicyDto,
  PayrollPolicyReviewDto,
} from "../../src/features/payroll/data/models/payroll-policy-dto";

export const payrollCompany = "10000000-0000-4000-8000-000000000001";
export const payrollActor = "c9000000-0000-4000-8000-000000000001";
export const payrollRule = {
  id: "ID-PP58-PMK168-2024-v1",
  effectiveFrom: "2024-01-01",
  effectiveUntil: null,
  sources: ["https://example.test/reviewed-tax-fixture"],
};
export function payrollPolicyRecord(
  version = 23,
  minimum = "4100000.25",
): PayrollPolicyReviewDto & { current: PayrollPolicyDto } {
  const current: PayrollPolicyDto = {
    version,
    appliedRevision: version,
    effectiveFrom: "2027-01",
    effectiveUntil: "2027-12",
    currency: "IDR",
    incomeTaxRuleId: payrollRule.id,
    insuranceRuleId: "ID-BPJS-PU-v1",
    minimumMonthlyWage: minimum,
    healthWageCap: "12000000.75",
    pensionWageCap: "10000000.50",
    contributionRounding: "HALF_UP",
    reviewReferences: ["https://example.test/fictional-company-policy"],
  };
  const history = Array.from({ length: version + 1 }, (_, revision) => ({
    policy: {
      ...current,
      appliedRevision: revision,
      effectiveFrom: revision === version ? "2027-01" : "2026-01",
      effectiveUntil: revision === version ? "2027-12" : "2026-12",
      minimumMonthlyWage: revision === version ? minimum : "4000000.50",
      reviewReferences: [...current.reviewReferences],
    },
    actorId: payrollActor,
    reason: `Company policy review ${revision}`,
    recordedAt: "2026-10-14T08:00:00Z",
  }));
  return {
    asOf: "2026-06",
    current,
    effective: history.at(-2)?.policy ?? null,
    history: { items: history, nextCursor: null },
  };
}
export function payrollPolicyPage(
  record: PayrollPolicyReviewDto,
  month = "2026-06",
  after: string | null = null,
): PayrollPolicyReviewDto {
  const candidates = record.history.items
    .filter((row) => row.policy.effectiveFrom <= month)
    .sort(
      (a, b) =>
        b.policy.effectiveFrom.localeCompare(a.policy.effectiveFrom) ||
        b.policy.appliedRevision - a.policy.appliedRevision,
    );
  const effective = candidates[0]?.policy;
  const rows = record.history.items.filter(
    (row) => row.policy.appliedRevision > (after === null ? -1 : Number(after)),
  );
  const items = rows.slice(0, 20);
  return {
    asOf: month,
    current: record.current,
    effective: effective && effective.effectiveUntil >= month ? effective : null,
    history: {
      items,
      nextCursor: rows.length > 20 ? String(items.at(-1)?.policy.appliedRevision) : null,
    },
  };
}
