import { expect, type Page, test } from "@playwright/test";
import { amendmentId } from "./fixtures/payroll-amendments";
import { calculationCompany } from "./fixtures/payroll-calculations";
import { companyIds } from "./identity-api";
import { installPayrollAmendmentAuthoringApi } from "./payroll-amendment-authoring-api";

const detail = `/payroll/amendments/${amendmentId()}?company=${calculationCompany}&year=2026`;
const command = (action: string) =>
  `/payroll/amendments/${amendmentId()}/${action}?company=${calculationCompany}&year=2026`;
async function confirm(page: Page, action: "Calculate proposal" | "Cancel proposal") {
  await page.getByRole("textbox", { name: "Reason", exact: true }).fill("Reviewed correction");
  await page.getByRole("button", { name: action, exact: true }).click();
}
async function openSaved(page: Page) {
  await page.getByRole("status").getByRole("link", { name: "View proposal", exact: true }).click();
}

test("calculate and cancel preserve the exact proposal, result and revision history", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page);
  await page.goto(detail);
  await page.getByRole("link", { name: "Calculate proposal", exact: true }).click();
  await confirm(page, "Calculate proposal");
  await expect(page.getByRole("status")).toContainText("Calculation attempt recorded.");
  await openSaved(page);
  await expect(page).toHaveURL(/revision=1/u);
  await expect(page.getByRole("table", { name: "Monthly comparison", exact: true })).toContainText(
    "2026-09",
  );
  await page.getByRole("link", { name: "View calculation basis", exact: true }).click();
  await expect(page).toHaveURL(/revision=0/u);
  await expect(
    page.getByRole("link", { name: /^(Calculate proposal|Cancel proposal)$/u }),
  ).toHaveCount(0);
  await page.getByRole("link", { name: "Current proposal", exact: true }).click();
  await page.getByRole("link", { name: "Cancel proposal", exact: true }).click();
  await page.getByRole("button", { name: "Cancel proposal", exact: true }).click();
  await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.count("cancel")).toBe(0);
  await confirm(page, "Cancel proposal");
  await expect(page.getByRole("status")).toContainText("Proposal cancelled.");
  await openSaved(page);
  await expect(page).toHaveURL(/revision=2/u);
  await expect(page.getByRole("region", { name: "Summary", exact: true })).toContainText(
    "Cancelled",
  );
  await expect(
    page.getByRole("link", { name: /^(Edit proposal|Calculate proposal|Cancel proposal)$/u }),
  ).toHaveCount(0);
  await page.getByRole("tab", { name: "Calculation", exact: true }).click();
  await expect(page.getByRole("table", { name: "Monthly comparison", exact: true })).toContainText(
    "2026-09",
  );
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "History", exact: true }).getByRole("row"),
  ).toHaveCount(4);
  expect(api.count("calculate")).toBe(1);
  expect(api.count("cancel")).toBe(1);
  expect(api.calculations.size).toBe(1);
  expect(api.identity.unhandled).toEqual([]);
});

test("a failed rule check records an inspectable attempt without reporting a successful calculation", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page, { calculationFails: true });
  await page.goto(command("calculate"));
  await confirm(page, "Calculate proposal");
  await expect(page.getByRole("status")).toContainText("Calculation attempt recorded.");
  await openSaved(page);
  await expect(page.getByRole("region", { name: "Calculation", exact: true })).toContainText(
    "Failed",
  );
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("table", { name: "Monthly comparison", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Calculate proposal", exact: true })).toBeVisible();
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await expect(page.getByRole("table", { name: "History", exact: true })).toContainText(
    "Calculation failed",
  );
  expect(api.drafts.get(amendmentId())?.at(-1)?.summary).toMatchObject({
    version: 1,
    status: "DRAFT",
  });
  expect(api.calculations.size).toBe(1);
});

for (const permission of ["payroll.read", "payroll.amend.review", "payroll.amend.publish"])
  test(`${permission} cannot start draft commands`, async ({ page }) => {
    const api = await installPayrollAmendmentAuthoringApi(page, { permissions: [permission] });
    await page.goto(detail);
    if (permission === "payroll.read")
      await expect(page.getByRole("alert")).toContainText("You do not have access");
    else await expect(page.getByRole("region", { name: "Summary", exact: true })).toBeVisible();
    await expect(
      page.getByRole("link", { name: /^(Calculate proposal|Cancel proposal)$/u }),
    ).toHaveCount(0);
    const reads = api.count("get");
    for (const action of ["calculate", "cancel"]) {
      await page.goto(command(action));
      await expect(page.getByRole("alert")).toContainText("You do not have access");
      expect(api.count(action)).toBe(0);
      expect(api.count("get")).toBe(reads);
    }
  });

for (const action of ["calculate", "cancel"] as const)
  test(`${action} retries the original command through response loss, MFA and a later conflict`, async ({
    page,
  }) => {
    const api = await installPayrollAmendmentAuthoringApi(page),
      label = action === "calculate" ? "Calculate proposal" : "Cancel proposal";
    await page.goto(command(action));
    api.loseNextCommand(action);
    await confirm(page, label);
    const retry = page.getByRole("button", { name: "Retry original command", exact: true });
    await expect(retry).toBeVisible();
    api.identity.expireMfa();
    api.failNext(action, "mfa_required", 403);
    await retry.click();
    await page
      .getByRole("alert")
      .getByRole("button", { name: "Account verification", exact: true })
      .click();
    const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
    await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
    await verification.getByRole("button", { name: "Verify", exact: true }).click();
    await expect(verification).toHaveCount(0);
    await expect(page.getByText("Amendment employee 1", { exact: true })).toHaveCount(0);
    await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toHaveCount(0);
    api.failNext(action, "stale_version", 409);
    await retry.click();
    await expect(retry).toBeEnabled();
    await retry.click();
    await expect(page.getByRole("status")).toContainText(
      action === "calculate" ? "Calculation attempt recorded." : "Proposal cancelled.",
    );
    const writes = api.requests.filter((row) => row.action === action);
    expect(writes).toHaveLength(4);
    for (const write of writes) expect(write).toEqual(writes[0]);
    expect(api.receipts.size).toBe(1);
    expect(api.count("get")).toBe(1);
    await expect(page.getByRole("main")).not.toContainText("PRIVATE authoring diagnostic");
  });

test("a concurrent edit requires deliberate reload before a new observed command", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page);
  await page.goto(command("calculate"));
  await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toBeVisible();
  const revisions = api.drafts.get(amendmentId()),
    current = revisions?.at(-1);
  if (!revisions || !current) throw new Error("Current draft required");
  revisions.push({
    ...structuredClone(current),
    summary: { ...current.summary, version: 1, reason: "Concurrent review" },
  });
  await confirm(page, "Calculate proposal");
  await expect(page.getByRole("button", { name: "Calculate proposal", exact: true })).toHaveCount(
    0,
  );
  const reload = page.getByRole("button", { name: "Reload review", exact: true });
  await reload.click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  expect(api.count("get")).toBe(1);
  await reload.click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toHaveValue("");
  await confirm(page, "Calculate proposal");
  await expect(page.getByRole("status")).toContainText("Calculation attempt recorded.");
  const writes = api.requests.filter((row) => row.action === "calculate");
  expect(writes.map((row) => row.input)).toEqual([
    { expectedVersion: 0, reason: "Reviewed correction" },
    { expectedVersion: 1, reason: "Reviewed correction" },
  ]);
  expect(writes[0]?.operation).not.toBe(writes[1]?.operation);
});

test("pending commands cannot publish a receipt into a replaced company", async ({ page }) => {
  const api = await installPayrollAmendmentAuthoringApi(page),
    held = api.holdNext("cancel");
  try {
    await page.goto(command("cancel"));
    await confirm(page, "Cancel proposal");
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("No amendments");
    held.release();
    await expect(page.getByText("Proposal cancelled.", { exact: true })).toHaveCount(0);
    await expect(page.getByText("Amendment employee 1", { exact: true })).toHaveCount(0);
  } finally {
    held.release();
  }
});

test("revoked command authority clears private review and input", async ({ page }) => {
  const api = await installPayrollAmendmentAuthoringApi(page);
  await page.goto(command("cancel"));
  api.failNext("cancel", "access_denied", 403);
  await confirm(page, "Cancel proposal");
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  await expect(page.getByText("Amendment employee 1", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Retry original command", exact: true }),
  ).toHaveCount(0);
  expect(api.receipts.size).toBe(0);
});

test("narrow dark Indonesian command review retains input without reacquisition", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page);
  await page.goto(command("calculate"));
  await page
    .getByRole("textbox", { name: "Reason", exact: true })
    .fill("Reviewed September correction");
  const reads = api.count("get");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("textbox", { name: "Alasan", exact: true })).toHaveValue(
    "Reviewed September correction",
  );
  expect(api.count("get")).toBe(reads);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-payroll-amendment-command-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
