import type {
  CompensationReviewDto,
  CompensationTermsDto,
  EmployeeCompensationDto,
} from "../../src/features/payroll/data/models/compensation-dto";
export const compensationCompany = "10000000-0000-4000-8000-000000000001";
export const compensationEmployee = "ca000000-0000-4000-8000-000000000001";
export const compensationActor = "ca100000-0000-4000-8000-000000000001";
export const compensationReference = {
  id: compensationEmployee,
  employeeNumber: "EMP-001",
  name: "Rani Pratama",
  employmentVersion: 7,
};
export function compensationTerms(): CompensationTermsDto {
  return {
    basicSalary: "10000000.25",
    fixedEarnings: [{ code: "MEAL", name: "Meal allowance", amount: "500000.75", taxable: true }],
    treatment: "GROSS",
    tax: {
      residency: "RESIDENT",
      ptkp: "TK0",
      residenceCountry: "ID",
      subjectiveFrom: null,
      subjectiveUntil: null,
      verifiedOn: "2026-01-15",
      verificationReference: "Verified fictional employee registration",
    },
    insuranceWage: "10000000.25",
    insurancePrograms: ["HEALTH", "OLD_AGE", "PENSION", "ACCIDENT", "DEATH"],
    insuranceExemptionReason: null,
    accidentRisk: "LOW",
    additionalHealthDependents: 0,
    additionalRetirementContribution: "100000.25",
    qualifiedDonation: "0",
    otherNetDeduction: "0",
    payBasis: {
      overtimeRuleId: "ID-PP35-2021-v1",
      holidayAllowanceRuleId: "ID-PERMENAKER6-2016-v1",
      proration: "CALENDAR_DAYS",
      workWeek: "FIVE_DAYS",
      shortestWorkDay: null,
      overtimeEligibility: "ELIGIBLE",
      overtimeExemptionReference: null,
      regularNonFixedWage: "0",
      serviceMonthConvention: "COMPLETE_MONTHS",
      rounding: "HALF_UP",
      reviewReference: "Reviewed fictional employment terms",
    },
  };
}
export function compensationRecord(
  version = 23,
): CompensationReviewDto & { current: EmployeeCompensationDto } {
  const history = Array.from({ length: version + 1 }, (_, revision) => ({
    compensation: {
      employeeId: compensationEmployee,
      employeeNumber: compensationReference.employeeNumber,
      employeeName: compensationReference.name,
      currency: "IDR" as const,
      version,
      appliedRevision: revision,
      effectiveFrom: revision === version ? "2027-01" : "2026-01",
      terms: {
        ...compensationTerms(),
        basicSalary: revision === version ? "12000000.75" : "10000000.25",
      },
    },
    actorId: compensationActor,
    reason: `Compensation review ${revision}`,
    recordedAt: "2026-10-14T08:00:00Z",
  }));
  const latest = history.at(-1);
  if (!latest) throw new Error("Fixture needs a revision");
  return {
    asOf: "2026-06",
    employee: { ...compensationReference },
    current: structuredClone(latest.compensation),
    effective: structuredClone(history.at(-2)?.compensation ?? null),
    canChange: true,
    history: { items: history, nextCursor: null },
  };
}
export function compensationPage(
  record: CompensationReviewDto,
  month = "2026-06",
  after: string | null = null,
): CompensationReviewDto {
  const effective =
    [...record.history.items]
      .filter((row) => row.compensation.effectiveFrom <= month)
      .sort(
        (a, b) =>
          b.compensation.effectiveFrom.localeCompare(a.compensation.effectiveFrom) ||
          b.compensation.appliedRevision - a.compensation.appliedRevision,
      )[0]?.compensation ?? null;
  const rows = record.history.items.filter(
      (row) => row.compensation.appliedRevision > (after === null ? -1 : Number(after)),
    ),
    items = rows.slice(0, 20);
  return structuredClone({
    asOf: month,
    employee: record.employee,
    current: record.current,
    effective,
    canChange: record.canChange,
    history: {
      items,
      nextCursor: rows.length > 20 ? String(items.at(-1)?.compensation.appliedRevision) : null,
    },
  });
}
