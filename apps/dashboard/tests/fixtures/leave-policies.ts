import type {
  LeavePolicyReviewDto,
  LeavePolicyRevisionDto,
  LeaveTypeDto,
} from "../../src/features/leave/data/models/leave-type-dto";

export const leavePolicyId = (company = 0, number = 1) =>
  `d900000${company}-0000-4000-8000-${String(number).padStart(12, "0")}`;

export function leavePolicyRecord(company = 0, number = 1, version = 0): LeavePolicyReviewDto {
  const name = `${company === 0 ? "North" : "South"} leave ${String(number).padStart(2, "0")}`;
  const current: LeaveTypeDto = {
    id: leavePolicyId(company, number),
    code: `LEAVE${String(number).padStart(3, "0")}`,
    name,
    paid: true,
    allowPartialDays: true,
    minServiceMonths: 3,
    allowedContracts: ["PERMANENT", "FIXED_TERM"],
    maxRequestDays: 14,
    attachmentRequired: true,
    accrual: { frequency: "MONTHLY", daysPerPeriod: "1.5", carryLimitDays: "3.5" },
    active: number !== 1,
    effectiveFrom: version > 0 ? "2027-01-01" : "2026-01-01",
    version,
    appliedRevision: version,
  };
  const items: LeavePolicyRevisionDto[] = Array.from({ length: version + 1 }, (_, index) => {
    const revision = version - index;
    return {
      name: revision === version ? name : `${name} / revision ${revision}`,
      paid: current.paid,
      allowPartialDays: current.allowPartialDays,
      minServiceMonths: revision === version ? 3 : 0,
      allowedContracts: [...current.allowedContracts],
      maxRequestDays: current.maxRequestDays,
      attachmentRequired: current.attachmentRequired,
      accrual: current.accrual ? { ...current.accrual } : null,
      active: revision === version ? current.active : true,
      effectiveFrom: revision === version ? current.effectiveFrom : "2026-01-01",
      revision,
      actorId: "a9000000-0000-4000-8000-000000000001",
      reason: `Policy review ${revision}`,
      recordedAt: `2026-06-${String(revision + 1).padStart(2, "0")}T02:30:00Z`,
    };
  });
  return { current, history: { items, nextCursor: null } };
}

export function leavePolicyReviewPage(record: LeavePolicyReviewDto, after: number | null = null) {
  const items = record.history.items.filter((row) => after === null || row.revision < after);
  return {
    current: record.current,
    history: {
      items: items.slice(0, 20),
      nextCursor: items.length > 20 ? String(items[19]?.revision) : null,
    },
  };
}
