import { expect, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { installSavedFiltersApi } from "./saved-report-filters-api";

for (const [kind, definitionVersion, month] of [
  ["ATTENDANCE_EXCEPTIONS", "attendance-exceptions.v1", false],
  ["ATTENDANCE_CLOSINGS", "attendance-closings.v1", true],
  ["LEAVE", "leave.v1", false],
  ["OVERTIME", "overtime.v1", false],
  ["EXPENSES", "expenses.v1", false],
  ["PAYROLL_COSTS", "payroll-costs.v1", true],
] as const) {
  test(`${kind} authoring retains its exact main report parameters`, async ({ page }) => {
    const api = await installSavedFiltersApi(page, { empty: true });
    const from = month ? "2026-01" : "2026-10-01",
      until = month ? "2026-12" : "2026-10-20";
    const query = new URLSearchParams({ company: companyIds[0], kind, from, until });
    if (kind === "OVERTIME") query.set("status", "APPROVED");
    await page.goto(`/reports/saved-filters/new?${query}`);
    await expect(page.getByLabel("From", { exact: true })).toHaveValue(from);
    await expect(page.getByLabel("Until", { exact: true })).toHaveValue(until);
    await page.getByLabel("Filter name", { exact: true }).fill("Report review");
    await page.getByRole("button", { name: "Save filter", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Filter saved.");
    expect(api.writes[0]?.body).toEqual({
      name: "Report review",
      kind,
      definitionVersion,
      expectedVersion: null,
      parameters: {
        asOf: null,
        from,
        until,
        companies: [],
        status: kind === "OVERTIME" ? "APPROVED" : null,
      },
    });
    await page.getByRole("link", { name: "Back to saved filters", exact: true }).click();
    await page.getByRole("button", { name: "Review: Report review", exact: true }).click();
    const open = page.getByRole("link", { name: "Open saved selection", exact: true });
    await expect(open).toHaveAttribute(
      "href",
      new RegExp(`company=${companyIds[0]}&from=${from}&until=${until}`, "u"),
    );
    expect(api.identity.unhandled).toEqual([]);
  });
}
