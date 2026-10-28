import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "./fixtures/admin-company";
import { createLeaveScenario } from "./fixtures/leave-scenario";

test("real leave totals reconcile with scoped request drill-down after withdrawal and retained return navigation", async ({
  page,
  context,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-leave-report-admin@example.invalid",
    "LEAVEREPORT",
    "Leave Report Browser",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const reviewer = "a0000000-0000-4000-8000-000000000081";
  expect(
    (
      await context.request.put(`/api/v1/companies/${company}/members/${reviewer}`, {
        headers: headers(),
        data: {
          active: true,
          permissions: ["leave.approve"],
          reason: "Independent leave report fixture review",
        },
      })
    ).status(),
  ).toBe(200);
  const scenario = await createLeaveScenario(context, company, reviewer);
  expect(
    (
      await context.request.post(`${scenario.base}/leave/requests`, {
        headers: headers(),
        data: {
          id: randomUUID(),
          employeeId: scenario.employee,
          typeId: scenario.type,
          days: [{ workDate: scenario.date, portion: "SECOND_HALF" }],
          reason: "Second half-day report fixture",
        },
      })
    ).status(),
  ).toBe(200);
  const reportUrl = `/reports/leave?company=${company}&from=${scenario.date}&until=${scenario.date}`;
  await page.goto(reportUrl);
  await expect(
    page.getByRole("region", { name: "Submitted days in period", exact: true }),
  ).toContainText("1");
  await expect(page.getByRole("region", { name: "Requests", exact: true })).toContainText("2");
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "1",
  );
  await page.getByRole("button", { name: "View requests: Pending", exact: true }).click();
  const table = page.getByRole("table", { name: "Requests", exact: true });
  await expect(table.getByRole("row")).toHaveCount(3);
  await expect(table.getByRole("cell", { name: "0.5", exact: true })).toHaveCount(4);
  await table
    .getByRole("button", {
      name: `View request: ${scenario.employeeName} · ${scenario.request}`,
      exact: true,
    })
    .click();
  await page.getByRole("link", { name: "Withdraw request", exact: true }).click();
  await page
    .getByLabel("Action reason", { exact: true })
    .fill("Withdraw reviewed report fixture request");
  await page.getByRole("button", { name: "Confirm action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Action recorded.");
  await page.getByRole("link", { name: "View current request", exact: true }).click();
  await page.getByRole("link", { name: "Back to report requests", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  expect(new URL(page.url()).searchParams.get("status")).toBe("PENDING");
  expect(new URL(page.url()).searchParams.get("from")).toBe(scenario.date);
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("CANCELLED");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(
    table.getByRole("button", {
      name: `View request: ${scenario.employeeName} · ${scenario.request}`,
      exact: true,
    }),
  ).toBeVisible();
  await expect(table).toContainText("Cancelled");
  await page.getByRole("link", { name: "Back to report", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Submitted days in period", exact: true }),
  ).toContainText("1");
  const statuses = page.getByRole("table", { name: "By current status", exact: true });
  await expect(
    statuses
      .getByRole("row")
      .filter({ has: page.getByRole("button", { name: "View requests: Pending", exact: true }) }),
  ).toContainText("0.5");
  await expect(
    statuses
      .getByRole("row")
      .filter({ has: page.getByRole("button", { name: "View requests: Cancelled", exact: true }) }),
  ).toContainText("0.5");
  const actual = await context.request.get(
    `${scenario.base}/reports/leave?from=${scenario.date}&until=${scenario.date}`,
  );
  expect(actual.status()).toBe(200);
  expect((await actual.json()).totals).toEqual({ requests: 2, employments: 1, days: "1" });
  await page.screenshot({
    path: "../../.work/dashboard-leave-report-real.png",
    fullPage: true,
    animations: "disabled",
  });
});
