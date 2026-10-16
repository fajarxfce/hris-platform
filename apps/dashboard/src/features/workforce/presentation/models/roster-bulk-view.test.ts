import { describe, expect, it } from "vitest";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import { identifier } from "../../di/calendar-fixture";
import { bulkReviewFixture } from "../../di/roster-bulk-fixture";
import { weekdays } from "../../domain/entities/schedule-assignment";
import { initialRosterBulkValues, rosterBulkDates } from "./roster-bulk-form-values";
import { rosterBulkFailureContext, rosterBulkView } from "./roster-bulk-view";

describe("bulk roster date controls and review presentation", () => {
  it("builds explicit selected weekdays across month, leap-year and daylight-saving boundaries", () => {
    expect(rosterBulkDates("2026-10-30", "2026-11-03", ["MONDAY", "FRIDAY"])).toEqual([
      "2026-10-30",
      "2026-11-02",
    ]);
    expect(rosterBulkDates("2028-02-28", "2028-03-01", weekdays)).toEqual([
      "2028-02-28",
      "2028-02-29",
      "2028-03-01",
    ]);
    expect(rosterBulkDates("2026-03-07", "2026-03-09", weekdays)).toHaveLength(3);
    expect(rosterBulkDates("9999-12-30", "9999-12-31", weekdays)).toEqual([
      "9999-12-30",
      "9999-12-31",
    ]);
    expect(initialRosterBulkValues("9999-12-30").until).toBe("9999-12-31");
  });
  it("rejects invalid or unbounded selections and permits an empty weekday intersection", () => {
    expect(rosterBulkDates("2026-10-01", "2026-10-31", weekdays)).toHaveLength(31);
    expect(rosterBulkDates("2026-10-01", "2026-11-01", weekdays)).toBeNull();
    expect(rosterBulkDates("2026-02-29", "2026-03-01", weekdays)).toBeNull();
    expect(rosterBulkDates("1899-12-31", "1900-01-01", weekdays)).toBeNull();
    expect(rosterBulkDates("2026-10-13", "2026-10-12", weekdays)).toBeNull();
    expect(rosterBulkDates("2026-10-12", "2026-10-13", [])).toBeNull();
    expect(rosterBulkDates("2026-10-12", "2026-10-13", ["MONDAY", "MONDAY"])).toBeNull();
    expect(rosterBulkDates("2026-10-12", "2026-10-13", ["SATURDAY"])).toEqual([]);
  });
  it("shows each employee/date with independent before and proposed schedules in both languages", () => {
    const review = bulkReviewFixture();
    const en = rosterBulkView(review, "en"),
      id = rosterBulkView(review, "id");
    expect(en.rows).toHaveLength(4);
    expect(new Set(en.rows.map((row) => row.id)).size).toBe(4);
    expect(en.rows[0]?.cells[2]).toContain("Asia/Jakarta");
    expect(en.rows[0]?.cells[3]).toContain("Day off");
    expect(id.columns[2]?.label).toBe("Saat ini");
    expect(id.rows[0]?.cells[4]).toBe("Perbarui");
  });
  it("adds error context only for selected employees and validated work dates", () => {
    const employees = bulkReviewFixture().employees.map((entry) => entry.employee);
    const failure = {
      code: "employee_unavailable",
      fields: {},
      parameters: { employeeId: employees[0]?.id ?? "", workDate: "2026-10-12" },
    };
    expect(rosterBulkFailureContext(failure, employees, "en")).toContain("Employee 1");
    expect(
      rosterBulkFailureContext(
        { ...failure, parameters: { ...failure.parameters, employeeId: identifier(777) } },
        employees,
        "en",
      ),
    ).toBeNull();
    expect(
      rosterBulkFailureContext(
        { ...failure, parameters: { ...failure.parameters, workDate: "2026-02-30" } },
        employees,
        "en",
      ),
    ).toBeNull();
    for (const code of [
      "invalid_roster_bulk_selection",
      "invalid_roster_bulk_change",
      "roster_bulk_changed",
      "roster_bulk_unchanged",
    ]) {
      expect(failureMessage({ ...failure, code }, "en")).not.toBe(
        failureMessage({ ...failure, code }, "id"),
      );
      expect(failureMessage({ ...failure, code }, "en")).not.toContain(code);
    }
  });
});
