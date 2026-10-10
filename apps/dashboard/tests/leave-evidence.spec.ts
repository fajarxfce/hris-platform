import { readFile } from "node:fs/promises";
import { expect, type Page, test } from "@playwright/test";
import { leaveId } from "./fixtures/leave";
import { companyIds } from "./identity-api";
import { installLeaveEvidenceApi } from "./leave-evidence-api";

const detail = `/leave/requests/${leaveId(0, 2)}?company=${companyIds[0]}`;
const action = `/leave/requests/${leaveId(0, 2)}/approve?company=${companyIds[0]}`;
const downloadButton = (page: Page) =>
  page.getByRole("button", { name: "Download: leave-evidence.pdf", exact: true });
async function assertDownload(page: Page, bytes: Buffer, name = "Download: leave-evidence.pdf") {
  const downloading = page.waitForEvent("download");
  await page.getByRole("button", { name, exact: true }).click();
  const download = await downloading;
  expect(download.suggestedFilename()).toBe("leave-evidence.pdf");
  const path = await download.path();
  if (!path) throw new Error("Expected evidence download");
  expect(await readFile(path)).toEqual(bytes);
}
test("detail downloads the selected immutable evidence only after an explicit click", async ({
  page,
}) => {
  const api = await installLeaveEvidenceApi(page);
  await page.goto(detail);
  await expect(downloadButton(page)).toBeEnabled();
  expect(api.downloads).toEqual([]);
  await assertDownload(page, api.evidence.bytes);
  await expect(page.getByRole("status")).toContainText("Download started.");
  expect(api.downloads).toHaveLength(1);
  expect(new URL(api.downloads[0] ?? "").pathname).toBe(
    `/api/v1/companies/${companyIds[0]}/leave/requests/${leaveId(0, 2)}/attachments/${api.evidence.attachment.revisionId}/content`,
  );
  expect(api.identity.unhandled).toEqual([]);
});
test("review downloads preserve the decision draft across locale and theme changes", async ({
  page,
}) => {
  const api = await installLeaveEvidenceApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(action);
  await page.getByLabel("Action reason", { exact: true }).fill("Dokumen telah diperiksa");
  const reads = api.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  expect(api.downloads).toEqual([]);
  await assertDownload(page, api.evidence.bytes, "Download: leave-evidence.pdf");
  await expect(page.getByRole("status")).toContainText("Download dimulai.");
  await expect(page.getByLabel("Alasan tindakan", { exact: true })).toHaveValue(
    "Dokumen telah diperiksa",
  );
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-leave-evidence-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});
test("cancelling a pending download suppresses its late bytes and allows a new explicit download", async ({
  page,
}) => {
  const api = await installLeaveEvidenceApi(page);
  await page.goto(detail);
  const held = api.holdDownload();
  const files: string[] = [];
  page.on("download", (value) => files.push(value.suggestedFilename()));
  try {
    await downloadButton(page).click();
    await held.entered;
    await expect(downloadButton(page)).toBeDisabled();
    await page.getByRole("button", { name: "Cancel download", exact: true }).click();
    await expect(downloadButton(page)).toBeEnabled();
    held.release();
    await assertDownload(page, api.evidence.bytes);
    await expect(page.getByRole("status")).toContainText("Download started.");
    expect(files).toEqual(["leave-evidence.pdf"]);
    expect(api.downloads).toHaveLength(2);
  } finally {
    held.release();
  }
});
test("a late revoked download cannot redact a refreshed request", async ({ page }) => {
  const api = await installLeaveEvidenceApi(page);
  await page.goto(detail);
  api.failDownload("leave_attachment_not_found");
  const held = api.holdDownload();
  try {
    await downloadButton(page).click();
    await held.entered;
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(downloadButton(page)).toBeEnabled();
    held.release();
    await assertDownload(page, api.evidence.bytes);
    await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
      "North Employee 02",
    );
    await expect(page.getByRole("alert")).toHaveCount(0);
  } finally {
    held.release();
  }
});
test("company replacement cancels evidence and ignores the previous company's response", async ({
  page,
}) => {
  const api = await installLeaveEvidenceApi(page);
  await page.goto(detail);
  const held = api.holdDownload();
  const files: string[] = [];
  page.on("download", (value) => files.push(value.suggestedFilename()));
  try {
    await downloadButton(page).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Leave requests", exact: true })).toContainText(
      "South Employee",
    );
    held.release();
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(page.getByRole("table", { name: "Leave requests", exact: true })).toContainText(
      "South Employee",
    );
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    expect(files).toEqual([]);
  } finally {
    held.release();
  }
});
test("revoked evidence clears private review and requires a current reload", async ({ page }) => {
  const api = await installLeaveEvidenceApi(page);
  await page.goto(action);
  await page.getByLabel("Action reason", { exact: true }).fill("Old review");
  api.failDownload("leave_attachment_not_found");
  await downloadButton(page).click();
  await expect(page.getByRole("alert")).toContainText("no longer accessible");
  await expect(page.getByRole("main")).not.toContainText("North Employee");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE STORAGE");
  await expect(page.getByRole("button", { name: "Confirm action", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Retry", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(downloadButton(page)).toBeEnabled();
  await expect(page.getByLabel("Action reason", { exact: true })).toHaveValue("");
  expect(api.writes).toEqual([]);
});
test("MFA renewal retains review input and requires an explicit new download", async ({ page }) => {
  const api = await installLeaveEvidenceApi(page);
  await page.goto(action);
  await page.getByLabel("Action reason", { exact: true }).fill("Reviewed evidence");
  api.identity.expireMfa();
  api.failDownload("mfa_required");
  await downloadButton(page).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByLabel("Action reason", { exact: true })).toHaveValue("Reviewed evidence");
  expect(api.downloads).toHaveLength(1);
  await assertDownload(page, api.evidence.bytes);
  expect(api.downloads).toHaveLength(2);
  expect(api.writes).toEqual([]);
});
for (const kind of ["etag", "body"] as const)
  test(`invalid ${kind} fails without native file handoff`, async ({ page }) => {
    const api = await installLeaveEvidenceApi(page);
    await page.goto(detail);
    const files: string[] = [];
    page.on("download", (value) => files.push(value.suggestedFilename()));
    api.malformedDownload(kind);
    await downloadButton(page).click();
    await expect(page.getByRole("alert")).toBeVisible();
    await expect(downloadButton(page)).toBeEnabled();
    expect(files).toEqual([]);
    await assertDownload(page, api.evidence.bytes);
    expect(files).toEqual(["leave-evidence.pdf"]);
  });
test("submitting a decision cancels pending evidence without interfering with its receipt", async ({
  page,
}) => {
  const api = await installLeaveEvidenceApi(page);
  await page.goto(action);
  const held = api.holdDownload();
  const files: string[] = [];
  page.on("download", (value) => files.push(value.suggestedFilename()));
  try {
    await downloadButton(page).click();
    await held.entered;
    await page.getByRole("button", { name: "Confirm action", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Action recorded.");
    held.release();
    await expect(downloadButton(page)).toHaveCount(0);
    expect(api.commits).toBe(1);
    expect(files).toEqual([]);
  } finally {
    held.release();
  }
});
