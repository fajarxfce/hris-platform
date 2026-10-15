import { describe, expect, it } from "vitest";
import {
  accrualDetails as details,
  accrualReview as review,
} from "../../di/leave-accrual-schedule-fixture";
import {
  leaveAccrualScheduleReviewView,
  leaveAccrualSchedulesView,
  leaveAccrualScheduleView,
} from "./leave-accrual-schedule-view";

describe("schedule presentation", () => {
  it("formats period dates in company time independently from the browser timezone", () => {
    const view = leaveAccrualScheduleReviewView(review, "en");
    expect(view.rows[0]?.cells[0]).toBe("October 2026");
    expect(view.rows[0]?.cells[1]).toContain("Nov 1, 2026");
    expect(view.properties).toContainEqual({ label: "Company timezone", value: "Asia/Jakarta" });
  });
  it("shows eligibility changes for each reviewed policy period", () => {
    const changed = {
      ...review,
      periods: review.periods.map((period, index) =>
        index === 1
          ? {
              ...period,
              type: {
                ...period.type,
                minServiceMonths: 12,
                allowedContracts: ["FIXED_TERM" as const],
              },
            }
          : period,
      ),
    };
    const view = leaveAccrualScheduleReviewView(changed, "en");
    expect(view.rows[1]?.cells.slice(4)).toEqual(["12", "Fixed term"]);
    expect(view.rows[0]?.cells[5]).toContain("Permanent");
    expect(leaveAccrualScheduleReviewView(changed, "id").rows[1]?.cells.slice(4)).toEqual([
      "12",
      "Kontrak",
    ]);
  });
  it("uses live per-period progress without replacing immutable header details", () => {
    const changed = {
      ...details,
      periods: details.periods.map((period, index) =>
        index === 0
          ? { ...period, jobStatus: "SUCCEEDED" as const, completedEmployees: 3 }
          : period,
      ),
    };
    const view = leaveAccrualScheduleView(changed, "id");
    expect(view.rows[0]?.cells.slice(2)).toEqual(["Selesai", "3 / 3"]);
    expect(view.rows[1]?.cells.slice(2)).toEqual(["Antre", "0 / 3"]);
    expect(view.references).toContainEqual({ label: "Versi kebijakan", value: "4" });
  });
  it("labels history rows with the reviewed type and first period", () => {
    const row = leaveAccrualSchedulesView([details.schedule], "id")[0];
    expect(row?.actionLabel).toBe("Annual leave · Oktober 2026");
    expect(row?.cells.slice(1, 5)).toEqual(["Bulanan", "Oktober 2026", "3", "3"]);
  });
});
