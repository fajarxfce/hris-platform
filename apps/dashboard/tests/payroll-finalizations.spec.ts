import { expect, test } from "@playwright/test";
import { calculationCompany, calculationId } from "./fixtures/payroll-calculations";
import { finalizationId, finalizationJob } from "./fixtures/payroll-finalizations";
import { installPayrollFinalizationsApi } from "./payroll-finalizations-api";

const directory = `/payroll/publications?company=${calculationCompany}&from=2026-01&until=2026-12`,
  overview = `/payroll/runs/${calculationId}/finalizations?company=${calculationCompany}`,
  start = `/payroll/runs/${calculationId}/finalize?company=${calculationCompany}`,
  detail = `/payroll/finalizations/${finalizationId}?company=${calculationCompany}`;

test("a finalizer discovers approved payroll without acquiring employee results or review authority", async ({
  page,
}) => {
  const api = await installPayrollFinalizationsApi(page);
  await page.goto(directory);
  await page
    .getByRole("button", { name: "Review finalization: Calculation 1 · 2026-09", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Reviewed totals", exact: true })).toContainText(
    "-IDR",
  );
  await expect(page.getByRole("link", { name: "Review publication", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: /^(Calculation|Approval review)$/u })).toHaveCount(0);
  expect(api.base.reads).toHaveLength(0);
  expect(api.writes).toHaveLength(0);
  await page.screenshot({
    path: "../../.work/dashboard-payroll-finalization-overview.png",
    fullPage: true,
    animations: "disabled",
  });
});

test("directory pagination and submitted filters retain bounded company-specific requests", async ({
  page,
}) => {
  const api = await installPayrollFinalizationsApi(page, { runs: 51 });
  await page.goto(directory);
  const table = page.getByRole("table", { name: "Finalization", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await expect(table).toContainText("Payroll reference 51");
  await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  const reads = api.reads.length;
  await page.getByLabel("From month", { exact: true }).fill("2026-10");
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No completed calculations");
  expect(api.reads.at(-1)?.searchParams.has("after")).toBe(false);
  expect(api.reads.at(-1)?.searchParams.get("from")).toBe("2026-10");
  expect(api.base.reads).toHaveLength(0);
});

test("a lost publication response retains its immutable command through a later conflict", async ({
  page,
}) => {
  const api = await installPayrollFinalizationsApi(page);
  await page.goto(start);
  await page.getByRole("button", { name: "Publish payroll", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("aria-invalid", "true");
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("Reason", { exact: true }).fill("Publish approved September payroll");
  const reads = api.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue(
    "Publish approved September payroll",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  expect(api.reads).toHaveLength(reads);
  api.dropNext();
  await page.getByRole("button", { name: "Publish payroll", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Retry original command", exact: true }),
  ).toBeVisible();
  api.failNext("payroll_already_finalized", 409);
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Retry original command", exact: true }),
  ).toBeEnabled();
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Publication requested.");
  expect(api.writes).toHaveLength(3);
  expect(api.writes[1]).toEqual(api.writes[0]);
  expect(api.writes[2]).toEqual(api.writes[0]);
  expect(api.records.size).toBe(1);
  const id = String(api.writes[0]?.body.id);
  await page.getByRole("link", { name: "View publication", exact: true }).click();
  await expect(page.getByRole("region", { name: "Publication status", exact: true })).toContainText(
    "Queued",
  );
  api.finish(id, "SUCCEEDED");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("status")).toContainText(
    "Payslips published. Payment is managed separately.",
  );
  await page.setViewportSize({ width: 390, height: 844 });
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-payroll-publication-dark-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});

test("a changed approval requires a new review before allocating another command", async ({
  page,
}) => {
  const api = await installPayrollFinalizationsApi(page);
  await page.goto(start);
  await page.getByLabel("Reason", { exact: true }).fill("Publish approved payroll");
  api.preparation.approvalVersion = 4;
  await page.getByRole("button", { name: "Publish payroll", exact: true }).click();
  await expect(page.getByRole("button", { name: "Reload review", exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Retry original command", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  expect(api.writes).toHaveLength(1);
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Publish the current approval");
  await page.getByRole("button", { name: "Publish payroll", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Publication requested.");
  expect(api.writes[0]?.body.expectedApprovalVersion).toBe(3);
  expect(api.writes[1]?.body.expectedApprovalVersion).toBe(4);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});

test("readers can inspect finalization without receiving the publication action", async ({
  page,
}) => {
  const api = await installPayrollFinalizationsApi(page, { permissions: ["payroll.read"] });
  await page.goto(overview);
  await expect(page.getByRole("region", { name: "Reviewed totals", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Review publication", exact: true })).toHaveCount(0);
  const reads = api.reads.length;
  await page.goto(start);
  await expect(page.getByRole("alert")).toHaveText("You do not have access to this resource.");
  expect(api.reads).toHaveLength(reads);
  expect(api.writes).toHaveLength(0);
});

for (const [code, text] of [
  ["payroll_approved_review_required", "approved"],
  ["payroll_finalization_capacity", "limit"],
] as const)
  test(`publication review explains ${code} without enabling submission`, async ({ page }) => {
    const api = await installPayrollFinalizationsApi(page, { blocker: code });
    await page.goto(start);
    await expect(page.getByRole("alert")).toContainText(new RegExp(text, "iu"));
    await expect(page.getByRole("button", { name: "Publish payroll", exact: true })).toHaveCount(0);
    expect(api.writes).toHaveLength(0);
  });

test("a cancelled attempt stays cancelled after a later attempt publishes the run", async ({
  page,
}) => {
  const api = await installPayrollFinalizationsApi(page, { initial: "CANCELLED", owned: false });
  api.append("ef000000-0000-4000-8000-000000000002", "Publish after cancellation", "SUCCEEDED");
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Publication status", exact: true })).toContainText(
    "Cancelled",
  );
  await expect(page.getByText("Payslips published.", { exact: false })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Job details", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Payroll finalization", exact: true }).click();
  const history = page.getByRole("table", { name: "Publication history", exact: true });
  await expect(history.getByRole("row")).toHaveCount(3);
  await expect(history.getByRole("row").nth(1)).toContainText("Not published");
  await page.getByRole("button", { name: "View publication: Publication 2", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Payslips published.");
});

test("a failed attempt exposes safe failure guidance and permits an explicit new review", async ({
  page,
}) => {
  await installPayrollFinalizationsApi(page, { initial: "FAILED" });
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Publication status", exact: true })).toContainText(
    "Failed",
  );
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("link", { name: "Job details", exact: true })).toHaveAttribute(
    "href",
    `/administration/jobs?company=${calculationCompany}&job=${finalizationJob}`,
  );
  await page.getByRole("link", { name: "Payroll finalization", exact: true }).click();
  await expect(page.getByRole("link", { name: "Review publication", exact: true })).toBeVisible();
});

test("pending parent navigation and replaced company ownership cannot reveal an obsolete publication", async ({
  page,
}) => {
  const api = await installPayrollFinalizationsApi(page, { initial: "QUEUED" }),
    held = api.holdNext();
  await page.goto(detail);
  await held.entered.promise;
  try {
    await expect(page.getByRole("link", { name: "Payroll finalization", exact: true })).toHaveCount(
      0,
    );
  } finally {
    held.released.resolve();
  }
  await expect(page.getByRole("region", { name: "Reviewed totals", exact: true })).toBeVisible();
  const old = api.holdNext();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await old.entered.promise;
  try {
    await page
      .getByRole("combobox", { name: "Company", exact: true })
      .selectOption("10000000-0000-4000-8000-000000000002");
    await expect(page.getByRole("heading", { name: "Finalization", exact: true })).toBeVisible();
  } finally {
    old.released.resolve();
  }
  await expect(page.getByRole("region", { name: "Reviewed totals", exact: true })).toHaveCount(0);
  await expect(
    page.getByText("Publish independently approved payroll", { exact: true }),
  ).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});

test("revocation clears publication facts while dark and narrow layouts stay bounded", async ({
  page,
}) => {
  const api = await installPayrollFinalizationsApi(page, { initial: "SUCCEEDED" });
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Reviewed totals", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  api.failNext("access_denied");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("region", { name: "Reviewed totals", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Job details", exact: true })).toHaveCount(0);
  expect(await page.content()).not.toContain("PRIVATE TECHNICAL DIAGNOSTIC");
});
