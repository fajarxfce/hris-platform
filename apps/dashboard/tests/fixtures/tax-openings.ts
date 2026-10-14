import type {
  TaxOpeningDto,
  TaxOpeningReviewDto,
  TaxOpeningTermsDto,
} from "../../src/features/payroll/data/models/tax-opening-dto";
export const openingCompany = "11111111-1111-4111-8111-111111111111",
  openingEmployee = "bc100000-0000-4000-8000-000000000001",
  openingId = "bc200000-0000-4000-8000-000000000001",
  openingActor = "bc300000-0000-4000-8000-000000000001",
  openingReviewer = "bc300000-0000-4000-8000-000000000002",
  openingYear = "2026";
export const openingReference = () => ({
  id: openingEmployee,
  employeeNumber: "TX-001",
  name: "Tax Opening Employee",
  employmentVersion: 7,
});
export function openingTerms(): TaxOpeningTermsDto {
  return {
    throughMonth: 6,
    residency: "RESIDENT",
    ptkp: "K0",
    reference: "Reviewed June history",
    history: {
      taxableGross: "60000000.25",
      retirementContributions: "600000.25",
      qualifiedDonations: "0",
      withheld: "1500000",
      employmentMonths: 6,
      previousEmployerNet: "0",
      previousEmployerWithheld: "0",
    },
  };
}
export function openingRecord(version = 0): TaxOpeningDto {
  return {
    id: openingId,
    employeeId: openingEmployee,
    year: 2026,
    version,
    terms: openingTerms(),
    status: "DRAFT",
    preparedBy: openingActor,
    verifiedBy: null,
    recordedAt: "2026-10-01T01:00:00Z",
    reason: `Prepare opening revision ${version}`,
  };
}
export function openingReview(
  current: TaxOpeningDto | null = openingRecord(),
  after: string | null = null,
): TaxOpeningReviewDto {
  const first = after === null ? 0 : Number(after) + 1,
    last = Math.min(first + 20, (current?.version ?? -1) + 1);
  const history = current
    ? Array.from({ length: Math.max(0, last - first) }, (_, i) => {
        const { terms: _terms, ...summary } =
          first + i === current.version ? current : openingRecord(first + i);
        return summary;
      })
    : [];
  return {
    year: 2026,
    employee: openingReference(),
    current,
    history: {
      items: history,
      nextCursor: current && last <= current.version ? String(last - 1) : null,
    },
    frozen: false,
    availableActions: ["PREPARE"],
  };
}
