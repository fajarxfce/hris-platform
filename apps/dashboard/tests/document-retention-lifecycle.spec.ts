import { expect, type Page, test } from "@playwright/test";
import { documentManagerPermissions } from "./document-actions-api";
import { installDocumentRetentionLifecycleApi } from "./document-retention-lifecycle-api";
import { documentHeaderId } from "./fixtures/documents";
import { companyIds } from "./identity-api";

const policyPath = "/documents/retention/policies",
  detailPath = `/documents/retention/documents/${documentHeaderId}`;
async function confirm(page: Page, title: string, reason: string, saved: string) {
  await page.getByRole("link", { name: title, exact: true }).click();
  await expect(page.getByRole("heading", { name: title, exact: true })).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill(reason);
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("status")).toContainText(saved);
  await page.getByRole("link", { name: "View retention", exact: true }).click();
  await expect(page.getByRole("region", { name: "Retention status", exact: true })).toBeVisible();
}

test("retention-only lookup validates the document reference and acquires only lifecycle metadata", async ({
  page,
}) => {
  const api = await installDocumentRetentionLifecycleApi(page);
  await page.goto(policyPath);
  await page.getByRole("button", { name: "Review document", exact: true }).click();
  await expect(page.getByText("Enter a valid document ID.", { exact: true })).toBeVisible();
  expect(api.reads).toHaveLength(0);
  await page.getByLabel("Document ID", { exact: true }).fill(documentHeaderId);
  await page.getByRole("button", { name: "Review document", exact: true }).click();
  await expect(page).toHaveURL(`${detailPath}?company=${companyIds[0]}`);
  await expect(page.getByRole("region", { name: "Retention status", exact: true })).toContainText(
    "No changes",
  );
  await expect(page.getByRole("link", { name: "Document details", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Archive document", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Place legal hold", exact: true })).toBeVisible();
  await expect(
    page.getByRole("navigation").getByRole("link", { name: "Document retention", exact: true }),
  ).toHaveAttribute("aria-current", "page");
  expect(api.reads.every((url) => url.pathname.includes(`/${documentHeaderId}/retention`))).toBe(
    true,
  );
  expect(api.identity.unhandled).toHaveLength(0);
});

test("archive snapshots stay fixed while legal hold survives restoration and explicit rearchiving", async ({
  page,
}) => {
  const api = await installDocumentRetentionLifecycleApi(page);
  await page.goto(detailPath);
  await confirm(page, "Archive document", "Archive reviewed record", "Document archived.");
  const archived = api.snapshot().archive;
  await confirm(page, "Place legal hold", "Preserve for review", "Legal hold recorded.");
  api.setArchiveDays(1);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("region", { name: "Retention status", exact: true })).toContainText(
    "365 days",
  );
  expect(api.snapshot().archive).toEqual(archived);
  await confirm(page, "Restore document", "Allow more evidence", "Document restored.");
  expect(api.snapshot()).toMatchObject({ archive: null, legalHold: true, version: 2 });
  await expect(page.getByRole("link", { name: "Release legal hold", exact: true })).toBeVisible();
  await confirm(page, "Archive document", "Rearchive with current policy", "Document archived.");
  expect(api.snapshot()).toMatchObject({
    archive: { retentionDays: 1, policyVersion: 1 },
    legalHold: true,
    version: 3,
  });
  await confirm(page, "Release legal hold", "Review completed", "Legal hold released.");
  expect(api.commands.map((command) => command.body.expectedVersion)).toEqual([null, 0, 1, 2, 3]);
  await page.getByRole("button", { name: "View change: Retention version 0", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Change details", exact: true });
  await expect(panel).toContainText("365 days");
  await expect(panel).toContainText("Archive reviewed record");
  await page.getByRole("button", { name: "Close details", exact: true }).click();
  await page.getByRole("button", { name: "View change: Retention version 3", exact: true }).click();
  await expect(panel).toContainText("1 day");
  await expect(panel).toContainText("Rearchive with current policy");
  expect(api.receipts.size).toBe(5);
  expect(api.identity.unhandled).toHaveLength(0);
});

test("lost lifecycle responses survive MFA and later conflict without duplicating a change", async ({
  page,
}) => {
  const api = await installDocumentRetentionLifecycleApi(page);
  await page.goto(`${detailPath}/hold`);
  await page.getByLabel("Reason", { exact: true }).fill("Original preservation review");
  api.loseNextResponse();
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The response was not confirmed.");
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
  await expect(page.getByRole("region", { name: "Retention status", exact: true })).toHaveCount(0);
  api.rejectCommand("stale_version");
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The response was not confirmed.");
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Legal hold recorded.");
  expect(api.commands).toEqual(Array.from({ length: 4 }, () => api.commands[0]));
  expect(api.receipts.size).toBe(1);
});

test("unfinished uploads prevent archive and require a separate explicit review", async ({
  page,
}) => {
  const api = await installDocumentRetentionLifecycleApi(page);
  api.setUploadActive(true);
  await page.goto(`${detailPath}/archive`);
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("provide a reason");
  expect(api.commands).toHaveLength(0);
  await page.getByLabel("Reason", { exact: true }).fill("Archive selection");
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("still being processed");
  await expect(page.getByRole("button", { name: "Confirm", exact: true })).toBeDisabled();
  expect(api.snapshot().version).toBeNull();
  api.setUploadActive(false);
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Uploads resolved");
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Document archived.");
  expect(api.commands).toHaveLength(2);
  expect(api.commands[0]?.operation).not.toBe(api.commands[1]?.operation);
});

test("company departure cancels the pending owner and discards document references", async ({
  page,
}) => {
  const api = await installDocumentRetentionLifecycleApi(page);
  await page.goto(`${detailPath}/hold`);
  await page.getByLabel("Reason", { exact: true }).fill("First company review");
  const held = api.holdCommand();
  try {
    await page.getByRole("button", { name: "Confirm", exact: true }).click();
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page).toHaveURL(`${policyPath}?company=${companyIds[1]}`);
    await expect(page.getByLabel("Document ID", { exact: true })).toHaveValue("");
    held.release();
    await expect(page.getByText("Legal hold recorded.", { exact: true })).toHaveCount(0);
    expect(api.reads.every((url) => url.pathname.includes(companyIds[0]))).toBe(true);
    expect(api.commands).toHaveLength(1);
  } finally {
    held.release();
  }
});

test("bounded history panels disappear before a failed access refresh", async ({ page }) => {
  const api = await installDocumentRetentionLifecycleApi(page, { historySize: 22 });
  await page.goto(detailPath);
  const table = page.getByRole("table", { name: "Retention history", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  await page
    .getByRole("button", { name: "View change: Retention version 20", exact: true })
    .click();
  await expect(page.getByRole("dialog", { name: "Change details", exact: true })).toContainText(
    "365 days",
  );
  await page.getByRole("button", { name: "Close details", exact: true }).click();
  const held = api.holdRead();
  api.rejectRead("document_access_denied");
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.started;
    await expect(table).toHaveCount(0);
    await expect(page.getByRole("region", { name: "Retention status", exact: true })).toHaveCount(
      0,
    );
    held.release();
    await expect(page.getByRole("alert")).toContainText("no longer have access");
    await expect(page.getByText("PRIVATE LIFECYCLE ERROR", { exact: true })).toHaveCount(0);
  } finally {
    held.release();
  }
});

test("document details link to retention only for the independent grant", async ({ page }) => {
  const api = await installDocumentRetentionLifecycleApi(page, {
    permissions: [...documentManagerPermissions, "documents.retention"],
  });
  await page.goto(`/documents/${documentHeaderId}`);
  await page
    .getByRole("main")
    .getByRole("link", { name: "Document retention", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Retention status", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Document details", exact: true })).toBeVisible();
  await expect(
    page.getByRole("navigation").getByRole("link", { name: "Document retention", exact: true }),
  ).toHaveAttribute("aria-current", "page");
  await expect(
    page.getByRole("navigation").getByRole("link", { name: "Documents", exact: true }),
  ).not.toHaveAttribute("aria-current", "page");
  expect(api.identity.unhandled).toHaveLength(0);
});

test("ordinary document management cannot review or change a document lifecycle", async ({
  page,
}) => {
  const api = await installDocumentRetentionLifecycleApi(page, {
    permissions: documentManagerPermissions,
  });
  await page.goto(`${detailPath}/archive`);
  await expect(page.getByRole("alert")).toContainText("access");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(0);
  expect(api.commands).toHaveLength(0);
});

test("narrow dark history preserves archive details without horizontal page overflow", async ({
  page,
}) => {
  await installDocumentRetentionLifecycleApi(page, { historySize: 3 });
  await page.setViewportSize({ width: 430, height: 900 });
  await page.goto(detailPath);
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await expect(page.getByRole("region", { name: "Retention status", exact: true })).toContainText(
    "365 days",
  );
  await page.screenshot({
    path: "../../.work/dashboard-document-retention-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.getByRole("button", { name: "View change: Retention version 0", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Change details", exact: true })).toBeInViewport();
  await page.screenshot({
    path: "../../.work/dashboard-document-retention-change-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await expect(page.getByRole("dialog", { name: "Change details", exact: true })).toContainText(
    "365 days",
  );
});
