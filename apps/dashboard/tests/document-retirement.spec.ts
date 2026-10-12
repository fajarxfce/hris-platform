import { expect, test } from "@playwright/test";
import { documentManagerPermissions } from "./document-actions-api";
import { installDocumentRetirementApi } from "./document-retirement-api";
import { documentHeaderId, documentRevisionId } from "./fixtures/documents";
import { companyIds } from "./identity-api";

const detailPath = `/documents/retention/documents/${documentHeaderId}`,
  retirementPath = `${detailPath}/revisions/${documentRevisionId}`,
  policyPath = "/documents/retention/policies";

test("retention-only operators review a revision without reading file or employee data", async ({
  page,
}) => {
  const api = await installDocumentRetirementApi(page);
  await page.goto(detailPath);
  await page.getByRole("button", { name: "Review retirement", exact: true }).click();
  await expect(page.getByText("Enter a valid revision ID.", { exact: true })).toBeVisible();
  await page.getByLabel("Revision ID", { exact: true }).fill(documentRevisionId);
  await page.getByRole("button", { name: "Review retirement", exact: true }).click();
  await expect(page).toHaveURL(`${retirementPath}?company=${companyIds[0]}`);
  await expect(page.getByRole("region", { name: "Revision review", exact: true })).toContainText(
    documentRevisionId,
  );
  await expect(page.getByRole("region", { name: "Retention status", exact: true })).toContainText(
    "365 days",
  );
  await expect(page.getByRole("button", { name: "Retire revision", exact: true })).toBeEnabled();
  expect(
    api.reads.every(
      (url) => url.pathname.includes("/retention") || url.pathname.endsWith("/retirement"),
    ),
  ).toBe(true);
  expect(api.identity.unhandled).toHaveLength(0);
});

for (const [blockingReason, message] of [
  ["document_retention_pending", "has not ended"],
  ["document_retention_indefinite", "indefinite retention"],
  ["document_legal_hold", "active legal hold"],
  ["document_evidence_referenced", "business record"],
  ["document_future_blocker", "Retirement unavailable"],
])
  test(`a server blocker prevents confirmation: ${blockingReason}`, async ({ page }) => {
    const api = await installDocumentRetirementApi(page, {
      review: { blockingReason: blockingReason ?? null },
    });
    await page.goto(retirementPath);
    await expect(page.getByRole("status")).toContainText(message ?? "");
    await expect(page.getByRole("button", { name: "Retire revision", exact: true })).toHaveCount(0);
    await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
    expect(api.commands).toHaveLength(0);
  });

test("lost retirement responses retain the original command across MFA and later conflicts", async ({
  page,
}) => {
  const api = await installDocumentRetirementApi(page);
  await page.goto(retirementPath);
  await page.getByLabel("Reason", { exact: true }).fill("Expired archive reviewed");
  api.loseNextResponse();
  await page.getByRole("button", { name: "Retire revision", exact: true }).click();
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
  api.rejectCommand("stale_version");
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The response was not confirmed.");
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText(
    "Content retired. File deletion is scheduled.",
  );
  expect(api.commands).toEqual(Array.from({ length: 4 }, () => api.commands[0]));
  expect(api.receipts.size).toBe(1);
  expect(api.snapshot()).toMatchObject({
    status: "RETIRED",
    published: false,
    retention: { version: 1 },
  });
  await page.getByRole("link", { name: "View retention", exact: true }).click();
  await page.getByRole("button", { name: "View change: Retention version 1", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Change details", exact: true })).toContainText(
    documentRevisionId,
  );
  await expect(page.getByRole("dialog", { name: "Change details", exact: true })).toContainText(
    "Expired archive reviewed",
  );
  expect(api.identity.unhandled).toHaveLength(0);
});

test("a new hold requires fresh review and cannot be bypassed by confirming again", async ({
  page,
}) => {
  const api = await installDocumentRetirementApi(page);
  await page.goto(retirementPath);
  await page.getByRole("button", { name: "Retire revision", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("provide a reason");
  expect(api.commands).toHaveLength(0);
  await page.getByLabel("Reason", { exact: true }).fill("Retire selected revision");
  api.update({
    blockingReason: "document_legal_hold",
    retention: { ...api.snapshot().retention, version: 1, legalHold: true },
  });
  await page.getByRole("button", { name: "Retire revision", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retire revision", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("active legal hold");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  expect(api.commands).toHaveLength(1);
  expect(api.receipts.size).toBe(0);
});

test("company departure disposes a pending retirement without restoring its late result", async ({
  page,
}) => {
  const api = await installDocumentRetirementApi(page);
  await page.goto(retirementPath);
  await page.getByLabel("Reason", { exact: true }).fill("First company retirement");
  const held = api.holdCommand();
  try {
    await page.getByRole("button", { name: "Retire revision", exact: true }).click();
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page).toHaveURL(`${policyPath}?company=${companyIds[1]}`);
    held.release();
    await expect(
      page.getByRole("heading", { name: "Content retirement", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByText("Content retired. File deletion is scheduled.", { exact: true }),
    ).toHaveCount(0);
    expect(api.reads.every((url) => url.pathname.includes(companyIds[0]))).toBe(true);
    expect(api.commands).toHaveLength(1);
  } finally {
    held.release();
  }
});

test("a revoked grant removes the review before a failed reload", async ({ page }) => {
  const api = await installDocumentRetirementApi(page);
  await page.goto(retirementPath);
  await expect(page.getByRole("region", { name: "Revision review", exact: true })).toBeVisible();
  api.rejectRead("access_denied");
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await expect(page.getByRole("region", { name: "Revision review", exact: true })).toHaveCount(0);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByText("PRIVATE RETIREMENT DETAIL", { exact: true })).toHaveCount(0);
  expect(api.commands).toHaveLength(0);
});

for (const grant of [true, false])
  test(`normal revision details respect independent retirement access: ${grant}`, async ({
    page,
  }) => {
    const api = await installDocumentRetirementApi(page, {
      permissions: [...documentManagerPermissions, ...(grant ? ["documents.retention"] : [])],
    });
    await page.goto(`/documents/${documentHeaderId}/revisions/${documentRevisionId}`);
    await expect(page.getByRole("region", { name: "Revision 1", exact: true })).toContainText(
      documentRevisionId,
    );
    const link = page.getByRole("link", { name: "Review retirement", exact: true });
    if (grant) {
      await link.click();
      await expect(
        page.getByRole("heading", { name: "Content retirement", exact: true }),
      ).toBeVisible();
    } else {
      await expect(link).toHaveCount(0);
      await page.goto(retirementPath);
      await expect(page.getByRole("alert")).toContainText("access");
      expect(api.reads.some((url) => url.pathname.endsWith("/retirement"))).toBe(false);
    }
    expect(api.identity.unhandled).toHaveLength(0);
  });

test("narrow retirement review supports both themes and Indonesian without page overflow", async ({
  page,
}) => {
  await installDocumentRetirementApi(page);
  await page.setViewportSize({ width: 430, height: 900 });
  await page.goto(retirementPath);
  await expect(page.getByRole("region", { name: "Revision review", exact: true })).toBeVisible();
  await page.screenshot({
    path: "../../.work/dashboard-document-retirement-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByRole("button", { name: "Hentikan revisi", exact: true })).toBeEnabled();
  await page.screenshot({
    path: "../../.work/dashboard-document-retirement-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
});
