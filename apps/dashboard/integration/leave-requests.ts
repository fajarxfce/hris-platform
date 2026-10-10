import { type BrowserContext, expect, type Page } from "@playwright/test";
import { createLeaveScenario } from "./fixtures/leave-scenario";

export async function reviewLeaveRequests(
  page: Page,
  context: BrowserContext,
  company: string,
  otherCompany: string,
  reviewer: string,
) {
  const { base, employee, request, employeeName } = await createLeaveScenario(
    context,
    company,
    reviewer,
  );
  const source = await context.request.get(`${base}/leave/requests/${request}?historyLimit=20`);
  expect(source.status()).toBe(200);
  expect(await source.json()).toMatchObject({
    id: request,
    version: 0,
    status: "PENDING",
    chargedDays: "0.5",
    history: { items: [{ kind: "SUBMITTED" }] },
  });
  expect(
    (
      await context.request.get(`/api/v1/companies/${otherCompany}/leave/requests/${request}`)
    ).status(),
  ).toBe(404);
  await page.getByRole("link", { name: "Employees", exact: true }).click();
  await page
    .getByRole("table", { name: "Employee directory", exact: true })
    .getByRole("row")
    .filter({ hasText: employeeName })
    .getByRole("button")
    .click();
  await page.getByRole("main").getByRole("link", { name: "Leave requests", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`employee=${employee}$`, "u"));
  const table = page.getByRole("table", { name: "Leave requests", exact: true });
  await expect(table).toContainText(employeeName);
  await table.getByRole("button", { name: new RegExp(`${request}$`, "u") }).click();
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
    "Browser personal leave",
  );
  await expect(
    page.getByRole("table", { name: "Submitted schedule (Asia/Jakarta)", exact: true }),
  ).toContainText("First half");
  await expect(page.getByRole("region", { name: "Submitted policy", exact: true })).toContainText(
    "Yes",
  );
  await expect(page.getByRole("table", { name: "Request history", exact: true })).toContainText(
    "Submitted",
  );
  await page.getByRole("button", { name: "View approval: Initial approval", exact: true }).click();
  await page.getByRole("link", { name: "View source request", exact: true }).click();
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
    employeeName,
  );
}
