import type {
  PayrollWorkAdjustmentDto,
  PayrollWorkDaysDto,
} from "../../src/features/payroll/data/models/payroll-work-dto";

export const workAdjustmentId = (revision = 1) =>
  `ac000000-0000-4000-8000-${String(revision).padStart(12, "0")}`;
export function workAdjustment(revision = 1, month = "2026-09"): PayrollWorkAdjustmentDto {
  return {
    id: workAdjustmentId(revision),
    revision,
    workDate: `${month}-01`,
    recordedAt: "2026-10-02T01:00:00Z",
    reason: `Verified attendance correction ${revision}`,
  };
}
export function workDays(month = "2026-09", adjusted = false): PayrollWorkDaysDto {
  const [year = 0, number = 0] = month.split("-").map(Number);
  return Array.from({ length: new Date(Date.UTC(year, number, 0)).getUTCDate() }, (_, i) => ({
    date: `${month}-${String(i + 1).padStart(2, "0")}`,
    schedule: "WORK",
    attendance: adjusted && i === 0 ? "WORKED" : "UNRECORDED",
    officialHoliday: false,
    overtime: [],
  }));
}
