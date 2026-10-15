import { describe, expect, it } from "vitest";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import { batchDetails, batchReview } from "../../di/leave-batch-fixture";
import { leaveBatchesView, leaveBatchReviewView, leaveBatchView } from "./leave-batch-view";

describe("leave process display", () => {
  it("distinguishes incomplete batch metadata from the actual terminal job", () => {
    expect(leaveBatchesView([batchDetails.batch], "en")[0]?.cells).toContain("Incomplete");
    const view = leaveBatchView(batchDetails, "en");
    expect(view.properties).toContainEqual({ label: "Status", value: "Failed" });
    expect(view.properties).toContainEqual({ label: "Processed", value: "2 / 3" });
    expect(view.properties).not.toContainEqual({ label: "Processed", value: "2 / 4" });
    expect(view.metrics).toContainEqual({ label: "Skipped", value: "1" });
  });
  it("uses localized per-employee failures without exposing raw metadata", () => {
    const result = leaveBatchView(batchDetails, "id").rows[1];
    expect(result?.cells[2]).toBe("Dilewati");
    expect(result?.cells[3]).toContain("belum memenuhi syarat");
    expect(result?.cells.join(" ")).not.toContain("leave_employee_ineligible");
  });
  it("shows the reviewed population without pretending all employees receive the same amount", () => {
    const view = leaveBatchReviewView(batchReview, "en");
    expect(view.properties).toContainEqual({ label: "Employees", value: "3" });
    expect(view.policy).toContainEqual({ label: "Days per period", value: "1" });
    expect(view.properties.some((row) => row.label.includes("Total days"))).toBe(false);
  });
});

it.each([
  "stale_policy_version",
  "leave_account_inconsistent",
  "leave_resolution_required",
  "leave_destination_year_closed",
  "employment_history_capacity",
  "leave_accrual_frequency_changed",
  "leave_batch_checkpoint_inconsistent",
])("localizes %s without falling back to technical details", (code) => {
  for (const locale of ["en", "id"] as const) {
    const translated = failureMessage({ code, fields: {}, parameters: {} }, locale);
    expect(translated).not.toBe(
      failureMessage({ code: "unknown_code", fields: {}, parameters: {} }, locale),
    );
    expect(translated).not.toContain(code);
  }
});
