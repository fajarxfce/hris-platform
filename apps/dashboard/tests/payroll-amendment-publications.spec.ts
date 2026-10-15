import { expect, type Page, test } from "@playwright/test";
import { amendmentPublicationId } from "./fixtures/payroll-amendment-publications";
import { amendmentReviewId } from "./fixtures/payroll-amendment-reviews";
import { amendmentCalculationId, amendmentId } from "./fixtures/payroll-amendments";
import { calculationCompany } from "./fixtures/payroll-calculations";
import { companyIds } from "./identity-api";
import { installPayrollAmendmentPublicationsApi } from "./payroll-amendment-publications-api";

const context = `company=${calculationCompany}&year=2026&query=EMP`,
  base = `/payroll/amendments/${amendmentId()}`,
  directory = `${base}/publications?${context}`,
  detail = `${base}/publications/${amendmentPublicationId()}?${context}`,
  start = `${base}/publish?${context}`,
  recover = `${base}/publications/${amendmentPublicationId()}/recover?${context}`;
async function confirm(page: Page, label: string, reason = "Publish reviewed correction") {
  await page.getByRole("textbox", { name: "Reason", exact: true }).fill(reason);
  await page.getByRole("button", { name: label, exact: true }).click();
}
async function openSaved(page: Page) {
  await page
    .getByRole("status")
    .getByRole("link", { name: "Open publication", exact: true })
    .click();
}

test("publication retains its approved basis, reports actual worker completion and links historical evidence", async ({
  page,
}) => {
  const api = await installPayrollAmendmentPublicationsApi(page);
  await page.goto(`${base}?${context}`);
  await page.getByRole("link", { name: "Amendment publications", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No publication attempts.");
  await page.getByRole("link", { name: "Publish amendment", exact: true }).click();
  await page.getByRole("button", { name: "Publish amendment", exact: true }).click();
  await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.count("start")).toBe(0);
  await confirm(page, "Publish amendment");
  await expect(page.getByRole("status")).toContainText("Publication requested.");
  const publication = api.attempts[0]?.publication.id;
  expect(publication).toBeTruthy();
  expect(publication).not.toBe(amendmentId());
  expect(api.requests.find((row) => row.action === "start")?.body).toEqual({
    expectedVersion: 3,
    reviewId: amendmentReviewId(),
    expectedReviewVersion: 1,
    expectedApprovalVersion: 1,
    calculationId: amendmentCalculationId,
    reason: "Publish reviewed correction",
  });
  await openSaved(page);
  await expect(page).toHaveURL(new RegExp(`/publications/${publication}\\?`, "u"));
  await expect(page.getByRole("region", { name: "Worker progress", exact: true })).toContainText(
    "Queued",
  );
  api.complete("PUBLISHED");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Corrected statements published.");
  await page.getByRole("link", { name: "Approved proposal", exact: true }).click();
  await expect(page).toHaveURL(/revision=3/u);
  await expect(
    page.getByRole("table", { name: "Observed assessments", exact: true }),
  ).toBeVisible();
  await page.goBack();
  await page.getByRole("link", { name: "Approved calculation", exact: true }).click();
  await expect(page).toHaveURL(/revision=3/u);
  await expect(page.getByRole("table", { name: "Monthly comparison", exact: true })).toBeVisible();
  await page.goBack();
  await page.getByRole("link", { name: "Review history", exact: true }).click();
  await expect(page.getByRole("table", { name: "History", exact: true })).toContainText(
    "Independent correction review",
  );
  await page.getByRole("link", { name: "Amendment publications", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Amendment publications", exact: true }),
  ).toContainText("Published");
  await expect(page.getByRole("link", { name: "Publish amendment", exact: true })).toHaveCount(0);
  expect(api.receipts.size).toBe(1);
  expect(api.identity.unhandled).toEqual([]);
});

test("an exhausted worker requires reviewed recovery and a separate publication command", async ({
  page,
}) => {
  const api = await installPayrollAmendmentPublicationsApi(page, { initial: "EXHAUSTED" });
  await page.goto(detail);
  await expect(page.getByRole("status")).toContainText("The worker has stopped.");
  await page.getByRole("link", { name: "Recover publication", exact: true }).click();
  await expect(
    page.getByText("This action does not start another publication.", { exact: false }),
  ).toBeVisible();
  await confirm(page, "Recover publication", "Close the exhausted attempt");
  await expect(page.getByRole("status")).toContainText("Attempt closed.");
  expect(api.count("start")).toBe(0);
  expect(api.requests.find((row) => row.action === "recover")?.body).toEqual({
    expectedVersion: 4,
    expectedJobVersion: 2,
    reason: "Close the exhausted attempt",
  });
  await openSaved(page);
  await expect(page.getByRole("status")).toContainText(
    "This attempt stopped without publishing the correction.",
  );
  await expect(page.getByRole("link", { name: "Recover publication", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Publications", exact: true }).click();
  await page.getByRole("link", { name: "Publish amendment", exact: true }).click();
  await confirm(page, "Publish amendment", "Retry the approved correction");
  await openSaved(page);
  expect(api.attempts).toHaveLength(2);
  expect(api.attempts[0]?.publication.status).toBe("STOPPED");
  expect(api.attempts[1]?.publication.status).toBe("PENDING");
});

for (const action of ["start", "recover"] as const)
  test(`${action} recovers one immutable command through lost response, MFA and a later version conflict`, async ({
    page,
  }) => {
    const api = await installPayrollAmendmentPublicationsApi(
      page,
      action === "recover" ? { initial: "EXHAUSTED" } : {},
    );
    await page.goto(action === "start" ? start : recover);
    api.loseNext(action);
    await confirm(page, action === "start" ? "Publish amendment" : "Recover publication");
    const retry = page.getByRole("button", { name: "Retry original command", exact: true });
    await expect(retry).toBeVisible();
    if (action === "start") api.complete("PUBLISHED");
    api.identity.expireMfa();
    api.failNext(action, "mfa_required", 403);
    await retry.click();
    await page
      .getByRole("alert")
      .getByRole("button", { name: "Account verification", exact: true })
      .click();
    const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
    await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
    await dialog.getByRole("button", { name: "Verify", exact: true }).click();
    await expect(dialog).toHaveCount(0);
    expect(api.count(action)).toBe(2);
    api.failNext(action, "stale_version");
    await retry.click();
    await expect(retry).toBeVisible();
    await retry.click();
    await expect(page.getByRole("status")).toContainText(
      action === "start" ? "Publication requested." : "Attempt closed.",
    );
    const writes = api.requests.filter((row) => row.action === action);
    expect(writes).toHaveLength(4);
    for (const command of writes) expect(command).toEqual(writes[0]);
    expect(api.receipts.size).toBe(1);
    await openSaved(page);
    await expect(page.getByRole("status")).toContainText(
      action === "start"
        ? "Corrected statements published."
        : "This attempt stopped without publishing the correction.",
    );
  });

for (const role of ["author", "reviewer", "reader"] as const)
  test(`${role} cannot start or recover publication`, async ({ page }) => {
    const api = await installPayrollAmendmentPublicationsApi(page, { role, initial: "EXHAUSTED" });
    await page.goto(detail);
    if (role === "reader")
      await expect(page.getByRole("alert")).toContainText("You do not have access");
    else
      await expect(
        page.getByRole("region", { name: "Worker progress", exact: true }),
      ).toContainText("Failed");
    await expect(page.getByRole("link", { name: "Recover publication", exact: true })).toHaveCount(
      0,
    );
    const reads = api.count("publications");
    await page.goto(recover);
    await expect(page.getByRole("alert")).toContainText("You do not have access");
    expect(api.count("publications")).toBe(reads);
    await page.goto(start);
    await expect(page.getByRole("alert")).toContainText("You do not have access");
    expect(api.count("start")).toBe(0);
    expect(api.count("recover")).toBe(0);
  });

test("eight stopped attempts remain inspectable without offering another publication", async ({
  page,
}) => {
  const api = await installPayrollAmendmentPublicationsApi(page, { count: 8 });
  await page.goto(directory);
  await expect(
    page.getByRole("table", { name: "Amendment publications", exact: true }).getByRole("row"),
  ).toHaveCount(9);
  await expect(page.getByRole("link", { name: "Publish amendment", exact: true })).toHaveCount(0);
  await page.goto(start);
  await expect(page.getByRole("alert")).toContainText("limit of eight publication attempts");
  expect(api.count("start")).toBe(0);
});

test("a definitively rejected publication hides private content and does not keep a retry command", async ({
  page,
}) => {
  const api = await installPayrollAmendmentPublicationsApi(page);
  await page.goto(start);
  api.failNext("start", "access_denied", 403);
  await confirm(page, "Publish amendment");
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  await expect(page.getByText("Amendment employee 1", { exact: true })).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Retry original command", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toHaveCount(0);
  expect(api.receipts.size).toBe(0);
});

test("pending recovery is discarded after a confirmed company change", async ({ page }) => {
  const api = await installPayrollAmendmentPublicationsApi(page, { initial: "EXHAUSTED" });
  await page.goto(recover);
  const held = api.holdNext("recover");
  try {
    await confirm(page, "Recover publication");
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("No amendments");
    held.release();
    await expect(page.getByText("Attempt closed.", { exact: false })).toHaveCount(0);
    await expect(page.getByText("Amendment employee 1", { exact: true })).toHaveCount(0);
  } finally {
    held.release();
  }
});

test("terminal detail stays stable across narrow dark layout and language changes", async ({
  page,
}) => {
  const api = await installPayrollAmendmentPublicationsApi(page, { initial: "PUBLISHED" });
  await page.goto(detail);
  await expect(page.getByRole("status")).toContainText("Corrected statements published.");
  const reads = api.count("publications");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("region", { name: "Dasar yang disetujui", exact: true }),
  ).toBeVisible();
  expect(api.count("publications")).toBe(reads);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-payroll-amendment-publication-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});

test("normal progress updates automatically and stops after publication", async ({ page }) => {
  const api = await installPayrollAmendmentPublicationsApi(page, { initial: "PENDING" });
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Worker progress", exact: true })).toContainText(
    "Queued",
  );
  api.complete("PUBLISHED");
  await expect(page.getByRole("status")).toContainText("Corrected statements published.", {
    timeout: 7000,
  });
  expect(api.count("publications")).toBe(2);
});
