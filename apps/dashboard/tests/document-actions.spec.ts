import { expect, test } from "@playwright/test";
import { actionRevisionId, installDocumentActionsApi } from "./document-actions-api";
import { documentHeaderId, documentId } from "./fixtures/documents";
import { companyIds } from "./identity-api";

const revisionPath = `/documents/${documentHeaderId}/revisions/${actionRevisionId}`;
test("current actions validate a completed file and explicitly cancel its pending validation", async ({
  page,
}) => {
  const api = await installDocumentActionsApi(page);
  await page.goto(revisionPath);
  await page.getByRole("link", { name: "Validate file", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Validate file", exact: true })).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill("Inspect replacement content");
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Validation requested.");
  expect(api.commands).toHaveLength(1);
  expect(api.commands[0]?.body).toEqual({
    expectedVersion: 3,
    reason: "Inspect replacement content",
  });
  await page.getByRole("link", { name: "View revision", exact: true }).click();
  await expect(page.getByText("Validating", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Validate file", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Cancel upload", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Replace the selected file");
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Upload cancelled.");
  expect(api.commands[1]?.body.expectedVersion).toBe(4);
  await page.getByRole("link", { name: "View revision", exact: true }).click();
  await expect(page.getByText("Cancelled", { exact: true })).toBeVisible();
  await expect(page.getByRole("group", { name: "Revision actions", exact: true })).toHaveCount(0);
  expect(api.receipts.size).toBe(2);
  expect(api.identity.unhandled).toHaveLength(0);
});

test("an uncertain command survives MFA recovery and replays only its original payload", async ({
  page,
}) => {
  const api = await installDocumentActionsApi(page);
  await page.goto(`${revisionPath}/validate`);
  await page.getByLabel("Reason", { exact: true }).fill("Inspect original selection");
  api.loseNextResponse();
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The response was not confirmed.");
  const original = api.commands[0];
  await expect(page.getByRole("button", { name: "Confirm", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Reload review", exact: true })).toHaveCount(0);
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
  await expect(page.getByText("replacement.pdf", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Validation requested.");
  expect(api.commands).toEqual([original, original, original]);
  expect(api.receipts.size).toBe(1);
  expect(api.reads.filter((url) => url.pathname.endsWith(actionRevisionId))).toHaveLength(1);
});

test("invalid reasons do not send commands and a stale version requires explicit review", async ({
  page,
}) => {
  const api = await installDocumentActionsApi(page);
  await page.goto(`${revisionPath}/cancel`);
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Enter a reason");
  expect(api.commands).toHaveLength(0);
  api.update({ version: 4 });
  await page.getByLabel("Reason", { exact: true }).fill("Close old selection");
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("button", { name: "Confirm", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("button", { name: "Confirm", exact: true })).toBeEnabled();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Close current selection");
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Upload cancelled.");
  expect(api.commands.map((command) => command.body.expectedVersion)).toEqual([3, 4]);
  expect(api.commands[0]?.operation).not.toBe(api.commands[1]?.operation);
});

test("company departure owns a pending command and hides its late completion", async ({ page }) => {
  const api = await installDocumentActionsApi(page);
  await page.goto(`${revisionPath}/cancel`);
  await page.getByLabel("Reason", { exact: true }).fill("Stop this upload");
  const held = api.holdCommand();
  try {
    await page.getByRole("button", { name: "Confirm", exact: true }).click();
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Stay on this page", exact: true })
      .click();
    await expect(page.getByText("replacement.pdf", { exact: true })).toBeVisible();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("table", { name: "Employees", exact: true })).toContainText(
      "South employee",
    );
    held.release();
    await expect(page).toHaveURL(`/documents?company=${companyIds[1]}`);
    await expect(page.getByText("replacement.pdf", { exact: true })).toHaveCount(0);
    await expect(page.getByText("Upload cancelled.", { exact: true })).toHaveCount(0);
    expect(api.commands).toHaveLength(1);
    expect(api.commands[0]?.path).toContain(companyIds[0]);
  } finally {
    held.release();
  }
});

test("a known denial stays gated during pending and failed review recovery", async ({ page }) => {
  const api = await installDocumentActionsApi(page);
  await page.goto(`${revisionPath}/cancel`);
  await page.getByLabel("Reason", { exact: true }).fill("Cancel selection");
  api.rejectCommand("document_access_denied");
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("You no longer have access");
  await expect(page.getByText("replacement.pdf", { exact: true })).toHaveCount(0);
  const held = api.holdRead();
  api.rejectRead("request_failed");
  try {
    await page.getByRole("button", { name: "Retry", exact: true }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await held.started;
    await expect(page.getByText("replacement.pdf", { exact: true })).toHaveCount(0);
    held.release();
    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page.getByText("replacement.pdf", { exact: true })).toHaveCount(0);
    await page.getByRole("button", { name: "Retry", exact: true }).click();
    await expect(page.getByText("replacement.pdf", { exact: true })).toBeVisible();
    expect(api.commands).toHaveLength(1);
    await expect(page.getByText("PRIVATE COMMAND ERROR", { exact: true })).toHaveCount(0);
  } finally {
    held.release();
  }
});

test("fresh action review rejects a capability withdrawn since the detail read", async ({
  page,
}) => {
  const api = await installDocumentActionsApi(page);
  await page.goto(revisionPath);
  await expect(page.getByRole("link", { name: "Validate file", exact: true })).toBeVisible();
  api.update({ availableActions: [] });
  await page.getByRole("link", { name: "Validate file", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This action is no longer available.");
  await expect(page.getByRole("button", { name: "Confirm", exact: true })).toHaveCount(0);
  expect(api.commands).toHaveLength(0);
});

test("read-only grants cannot authorize actions even when cached transport suggests them", async ({
  page,
}) => {
  const api = await installDocumentActionsApi(page, {
    permissions: ["company.read", "documents.read", "people.profile.read"],
  });
  await page.goto(revisionPath);
  await expect(page.getByText("replacement.pdf", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Cancel upload", exact: true })).toHaveCount(0);
  const reads = api.reads.length;
  await page.goto(`${revisionPath}/cancel`);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(reads);
  expect(api.commands).toHaveLength(0);
});

test("expired active uploads remain explicitly cancellable in Indonesian and dark mode", async ({
  page,
}) => {
  const api = await installDocumentActionsApi(page, {
    revision: { status: "EXPIRED", availableActions: ["CANCEL_UPLOAD"] },
  });
  await page.goto(revisionPath);
  await expect(page.getByRole("link", { name: "Validate file", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Cancel upload", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("heading", { name: "Batalkan upload", exact: true })).toBeVisible();
  await expect(page.getByText("Kedaluwarsa", { exact: true })).toBeVisible();
  await page.getByLabel("Alasan", { exact: true }).fill("File pengganti sudah disiapkan");
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({ path: "../../.work/dashboard-document-cancel-dark.png", fullPage: true });
  await page.getByRole("button", { name: "Konfirmasi", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Upload dibatalkan.");
  expect(api.commands).toHaveLength(1);
});

test("a failed inspection offers an explicit validation retry with its observed version", async ({
  page,
}) => {
  const api = await installDocumentActionsApi(page, {
    revision: {
      status: "VALIDATION_FAILED",
      validationAttempts: 1,
      validationJobId: documentId(41),
      failureCode: "request_timeout",
    },
  });
  await page.goto(revisionPath);
  await page.getByRole("link", { name: "Retry validation", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Retry after inspection service recovery");
  await page.screenshot({
    path: "../../.work/dashboard-document-validate-light.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Validation requested.");
  expect(api.commands).toHaveLength(1);
});
