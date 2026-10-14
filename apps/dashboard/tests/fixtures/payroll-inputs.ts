import type {
  PayrollInputDto,
  PayrollInputReviewDto,
  PayrollInputTermsDto,
} from "../../src/features/payroll/data/models/payroll-input-dto";
export const inputCompany = "11111111-1111-4111-8111-111111111111",
  inputEmployee = "ba100000-0000-4000-8000-000000000001",
  inputId = "ba200000-0000-4000-8000-000000000001",
  inputActor = "ba300000-0000-4000-8000-000000000001",
  inputReviewer = "ba300000-0000-4000-8000-000000000002",
  inputJob = "ba400000-0000-4000-8000-000000000001",
  inputMonth = "2026-09";
export const inputReference = () => ({
  id: inputEmployee,
  employeeNumber: "IN-001",
  name: "Payroll Input Employee",
  employmentVersion: 7,
});
export function inputTerms(): PayrollInputTermsDto {
  return {
    earnings: [{ code: "BONUS", name: "Monthly bonus", amount: "2000000.25", taxable: true }],
    deductions: [{ code: "MEAL", name: "Meal adjustment", amount: "125000.50" }],
    nonCashTaxable: "0",
    scheduledMonthUnits: "22.5",
    dayResolutions: [
      {
        workDate: "2026-09-02",
        portion: "FIRST_HALF",
        disposition: "PAID",
        reference: "Attendance review",
      },
    ],
    holidayAllowance: null,
    reviewReference: "September input review",
  };
}
export function inputRecord(version = 0): PayrollInputDto {
  return {
    id: inputId,
    employeeId: inputEmployee,
    earningsMonth: inputMonth,
    version,
    workJobId: inputJob,
    workPeriodVersion: 2,
    employmentVersion: 7,
    terms: inputTerms(),
    status: "DRAFT",
    preparedBy: inputActor,
    verifiedBy: null,
    recordedAt: "2026-10-01T01:00:00Z",
    reason: `Prepare input revision ${version}`,
  };
}
export function inputReview(
  current: PayrollInputDto | null = inputRecord(),
  after: string | null = null,
): PayrollInputReviewDto {
  const first = after === null ? 0 : Number(after) + 1,
    last = Math.min(first + 20, (current?.version ?? -1) + 1);
  const history = current
    ? Array.from({ length: last - first }, (_, i) => {
        const {
          terms: _t,
          workJobId: _j,
          workPeriodVersion: _w,
          employmentVersion: _e,
          ...summary
        } = first + i === current.version ? current : inputRecord(first + i);
        return summary;
      })
    : [];
  return {
    earningsMonth: inputMonth,
    employee: inputReference(),
    source: {
      periodId: "ba500000-0000-4000-8000-000000000001",
      earningsMonth: inputMonth,
      version: 2,
      closed: true,
      jobId: inputJob,
      includesEmployee: true,
    },
    current,
    history: {
      items: history,
      nextCursor: current && last <= current.version ? String(last - 1) : null,
    },
    frozen: false,
    availableActions: ["PREPARE"],
  };
}
