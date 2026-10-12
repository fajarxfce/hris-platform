import { expect, test } from "@playwright/test";
import { documentManagerPermissions } from "./document-actions-api";
import { installDocumentRetentionApi } from "./document-retention-api";
import { retentionPolicyId } from "./fixtures/document-retention";
import { companyIds } from "./identity-api";

const policiesPath = "/documents/retention-policies",
  policyPath = `${policiesPath}/personal`;
test("retention-only access has its own navigation and does not acquire document contents", async ({
  page,
}) => {
  const api = await installDocumentRetentionApi(page);
  await page.goto(policiesPath);
  await expect(
    page.getByRole("heading", { name: "Document retention", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("navigation").getByRole("link", { name: "Document retention", exact: true }),
  ).toHaveAttribute("aria-current", "page");
  await expect(
    page.getByRole("navigation").getByRole("link", { name: "Documents", exact: true }),
  ).toHaveCount(0);
  const table = page.getByRole("table", { name: "Policies", exact: true });
  await expect(table).toContainText("365 days");
  await expect(table.getByText("Not configured", { exact: true })).toHaveCount(2);
  await page.getByRole("button", { name: "View policy: Personal", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current policy", exact: true })).toContainText(
    "365 days",
  );
  await expect(page.getByRole("table", { name: "Policy history", exact: true })).toContainText(
    "Document retention policy",
  );
  expect(api.commands).toHaveLength(0);
  expect(api.reads.every((url) => url.pathname.includes("/documents/retention-policies"))).toBe(
    true,
  );
  expect(api.identity.unhandled).toHaveLength(0);
});

test("ordinary document management cannot read or edit retention", async ({ page }) => {
  const api = await installDocumentRetentionApi(page, { permissions: documentManagerPermissions });
  await page.goto(`${policyPath}/edit`);
  await expect(page.getByRole("alert")).toContainText("access");
  await expect(
    page.getByRole("navigation").getByRole("link", { name: "Document retention", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByLabel("Retention days", { exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(0);
  expect(api.commands).toHaveLength(0);
});

test("configures an indefinite policy and then records a finite revision with immutable history", async ({
  page,
}) => {
  const api = await installDocumentRetentionApi(page, { versions: 0 });
  await page.goto(policyPath);
  await expect(page.getByRole("region", { name: "Current policy", exact: true })).toContainText(
    "Not configured",
  );
  await page.getByRole("link", { name: "Configure policy", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Retain until separately reviewed");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Retention policy saved.");
  expect(api.commands[0]?.body).toMatchObject({
    classification: "PERSONAL",
    expectedVersion: null,
    retentionDays: null,
  });
  await page.getByRole("link", { name: "View policy", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current policy", exact: true })).toContainText(
    "Indefinite",
  );
  await page.getByRole("link", { name: "Edit policy", exact: true }).click();
  await page.getByLabel("Retention days", { exact: true }).fill("730");
  await page.getByLabel("Reason", { exact: true }).fill("Approved two-year period");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Retention policy saved.");
  expect(api.commands[1]?.body).toMatchObject({
    policyId: api.commands[0]?.body.policyId,
    expectedVersion: 0,
    retentionDays: 730,
  });
  await page.getByRole("link", { name: "View policy", exact: true }).click();
  await expect(page.getByRole("table", { name: "Policy history", exact: true })).toContainText(
    "Retain until separately reviewed",
  );
  await expect(page.getByRole("table", { name: "Policy history", exact: true })).toContainText(
    "Approved two-year period",
  );
  expect(api.receipts.size).toBe(2);
  expect(api.identity.unhandled).toHaveLength(0);
});

test("a lost response remains pinned through MFA and later version rejection", async ({ page }) => {
  const api = await installDocumentRetentionApi(page);
  await page.goto(`${policyPath}/edit`);
  await expect(page.getByLabel("Retention days", { exact: true })).toHaveValue("365");
  await page.getByLabel("Retention days", { exact: true }).fill("730");
  await page.getByLabel("Reason", { exact: true }).fill("Original policy review");
  api.loseNextResponse();
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The response was not confirmed.");
  await expect(page.getByRole("button", { name: "Save policy", exact: true })).toBeDisabled();
  await expect(page.getByLabel("Retention days", { exact: true })).toHaveValue("730");
  api.identity.expireMfa();
  api.rejectCommand("mfa_required");
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  expect(api.commands).toHaveLength(2);
  await expect(page.getByRole("region", { name: "Current policy", exact: true })).toHaveCount(0);
  api.rejectCommand("stale_version");
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The response was not confirmed.");
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Retention policy saved.");
  expect(api.commands).toEqual(Array.from({ length: 4 }, () => api.commands[0]));
  expect(api.receipts.size).toBe(1);
});

test("invalid values make no request and a definite conflict requires explicit current review", async ({
  page,
}) => {
  const api = await installDocumentRetentionApi(page);
  await page.goto(`${policyPath}/edit`);
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Provide a reason");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed duration");
  await page.getByLabel("Retention days", { exact: true }).fill("0");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Use 1–36,500");
  expect(api.commands).toHaveLength(0);
  await page.getByLabel("Retention days", { exact: true }).fill("730");
  api.revisePolicy({ retentionDays: 400 });
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("button", { name: "Save policy", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Retention days", { exact: true })).toHaveValue("400");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Retention days", { exact: true }).fill("730");
  await page.getByLabel("Reason", { exact: true }).fill("Current policy review");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Retention policy saved.");
  expect(api.commands.map((command) => command.body.expectedVersion)).toEqual([0, 1]);
  expect(api.commands[0]?.operation).not.toBe(api.commands[1]?.operation);
  expect(api.commands[1]?.body.policyId).toBe(retentionPolicyId);
});

test("history pages are bounded and stay in ascending version order", async ({ page }) => {
  const api = await installDocumentRetentionApi(page, { versions: 22 });
  await page.goto(policyPath);
  const history = page.getByRole("table", { name: "Policy history", exact: true });
  await expect(history.getByRole("row")).toHaveCount(21);
  await expect(history.getByRole("row").nth(1).getByRole("cell").first()).toHaveText("0");
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(3);
  await expect(history.getByRole("row").nth(1).getByRole("cell").first()).toHaveText("20");
  await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  expect(
    api.reads.filter((url) => url.pathname.endsWith("/history")).map((url) => url.search),
  ).toEqual(["?limit=20", "?limit=20&after=19"]);
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(21);
});

test("company departure cancels a pending save and drops old policy references", async ({
  page,
}) => {
  const api = await installDocumentRetentionApi(page);
  await page.goto(`${policyPath}/edit`);
  await page.getByLabel("Reason", { exact: true }).fill("First company review");
  const held = api.holdCommand();
  try {
    await page.getByRole("button", { name: "Save policy", exact: true }).click();
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Stay on this page", exact: true })
      .click();
    await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("First company review");
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page).toHaveURL(`${policiesPath}?company=${companyIds[1]}`);
    await expect(
      page
        .getByRole("table", { name: "Policies", exact: true })
        .getByText("Not configured", { exact: true }),
    ).toHaveCount(3);
    held.release();
    await expect(page.getByText("Retention policy saved.", { exact: true })).toHaveCount(0);
    expect(
      api.reads
        .filter((url) => url.pathname.includes(companyIds[1]))
        .every((url) => url.pathname.endsWith("/retention-policies")),
    ).toBe(true);
    expect(api.identity.unhandled).toHaveLength(0);
  } finally {
    held.release();
  }
});

test("denied policy details stay hidden during a failed refresh", async ({ page }) => {
  const api = await installDocumentRetentionApi(page);
  await page.goto(`${policyPath}/edit`);
  await page.getByLabel("Reason", { exact: true }).fill("Review");
  api.rejectCommand("access_denied");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current policy", exact: true })).toHaveCount(0);
  const held = api.holdRead();
  api.rejectRead("request_failed");
  try {
    await page.getByRole("button", { name: "Retry", exact: true }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await held.started;
    await expect(page.getByLabel("Retention days", { exact: true })).toHaveCount(0);
    held.release();
    await expect(page.getByRole("button", { name: "Retry", exact: true })).toBeVisible();
    await expect(page.getByLabel("Retention days", { exact: true })).toHaveCount(0);
    await expect(page.getByText("PRIVATE RETENTION FAILURE", { exact: true })).toHaveCount(0);
  } finally {
    held.release();
  }
});

test("narrow dark Indonesian policy editing retains readable controls", async ({ page }) => {
  await installDocumentRetentionApi(page, { indefinite: true });
  await page.setViewportSize({ width: 430, height: 900 });
  await page.goto(`${policyPath}/edit`);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Kebijakan retensi", exact: true })).toBeVisible();
  await expect(page.getByLabel("Masa simpan dalam hari", { exact: true })).toHaveValue("");
  await expect(page.getByRole("region", { name: "Kebijakan saat ini", exact: true })).toContainText(
    "Tanpa batas waktu",
  );
  await page.screenshot({
    path: "../../.work/dashboard-retention-policy-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
});
