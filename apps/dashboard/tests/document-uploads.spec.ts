import { createHash } from "node:crypto";
import { expect, type Page, test } from "@playwright/test";
import { installDocumentUploadsApi } from "./document-uploads-api";
import { documentEmployeeId, documentHeaderId } from "./fixtures/documents";
import { companyIds } from "./identity-api";

const folder = `/documents/employees/${documentEmployeeId}`;
const smallFile = Buffer.from("%PDF-1.4\nBrowser upload fixture\n%%EOF\n");
async function choose(page: Page, bytes = smallFile, name = "record.pdf", button = "Choose file") {
  const chooser = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: button, exact: true }).click();
  await (await chooser).setFiles({ name, mimeType: "application/pdf", buffer: bytes });
}
async function newUpload(page: Page, bytes = smallFile) {
  await page.goto(`${folder}/upload`);
  await page.getByLabel("Title", { exact: true }).fill("Signed employment record");
  await page.getByLabel("Classification", { exact: true }).selectOption("HR_ONLY");
  await page.getByLabel("Reason", { exact: true }).fill("Employee onboarding");
  await choose(page, bytes);
  await expect(page.getByRole("button", { name: "Change file", exact: true })).toBeEnabled();
}

test("employee folders upload a multipart file with its original checksum and a separate validation action", async ({
  page,
}) => {
  const api = await installDocumentUploadsApi(page);
  await page.goto(folder);
  await page.getByRole("link", { name: "Upload document", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Upload document", exact: true })).toBeVisible();
  await page.getByLabel("Title", { exact: true }).fill("Signed employment record");
  await page.getByLabel("Classification", { exact: true }).selectOption("HR_ONLY");
  await page.getByLabel("Reason", { exact: true }).fill("Employee onboarding");
  const bytes = Buffer.alloc(2 * 1024 * 1024 + 17, 11);
  await choose(page, bytes);
  await expect(page.getByRole("button", { name: "Change file", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Start upload", exact: true }).click();
  await expect(page.getByRole("status")).toContainText(
    "Upload complete. The file must pass validation",
  );
  expect(api.starts).toHaveLength(1);
  expect(api.starts[0]?.body).toMatchObject({
    title: "Signed employment record",
    employmentId: documentEmployeeId,
    classification: "HR_ONLY",
    size: bytes.byteLength,
    sha256: createHash("sha256").update(bytes).digest("hex"),
  });
  expect(api.chunks.map((part) => part.bytes.byteLength)).toEqual([1024 * 1024, 1024 * 1024, 17]);
  expect(Buffer.concat(api.chunks.map((part) => part.bytes))).toEqual(bytes);
  expect(new Set(api.chunks.map((part) => part.operation)).size).toBe(3);
  await page.getByRole("link", { name: "Validate file", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Validate file", exact: true })).toBeVisible();
  expect(api.identity.unhandled).toEqual([]);
});

test("replacement uploads retain document identity and its published revision", async ({
  page,
}) => {
  const api = await installDocumentUploadsApi(page);
  const published = api.documents.get(documentHeaderId)?.currentRevisionId;
  await page.goto(`/documents/${documentHeaderId}`);
  await page.getByRole("link", { name: "Upload revision", exact: true }).click();
  await expect(page.getByLabel("Title", { exact: true })).toHaveCount(0);
  await expect(page.getByText("Employment certificate", { exact: true })).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill("Renew the signed copy");
  await choose(page);
  await expect(page.getByRole("button", { name: "Change file", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Start upload", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Upload complete.");
  expect(api.starts[0]?.body).toMatchObject({
    documentId: documentHeaderId,
    expectedDocumentVersion: 2,
    title: "Employment certificate",
    classification: "PERSONAL",
  });
  expect(api.documents.get(documentHeaderId)?.currentRevisionId).toBe(published);
});

test("a lost chunk response resumes from the server offset without uploading committed bytes twice", async ({
  page,
}) => {
  const api = await installDocumentUploadsApi(page);
  const bytes = Buffer.alloc(1024 * 1024 + 19, 7);
  await newUpload(page, bytes);
  api.loseChunk();
  await page.getByRole("button", { name: "Start upload", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Upload paused.");
  await expect(page.getByLabel("Title", { exact: true })).toHaveAttribute("readonly", "");
  await expect(page.getByRole("button", { name: "Change file", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Retry upload", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Upload complete.");
  expect(api.starts).toHaveLength(2);
  expect(api.starts[1]).toEqual(api.starts[0]);
  expect(api.chunks.map((part) => part.offset)).toEqual([0, 1024 * 1024]);
  expect(Buffer.concat(api.chunks.map((part) => part.bytes))).toEqual(bytes);
});

test("a new page resumes a reserved part with the original operation after reselecting identical content", async ({
  page,
}) => {
  const api = await installDocumentUploadsApi(page);
  await newUpload(page);
  api.reserveChunk();
  await page.getByRole("button", { name: "Start upload", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Upload paused.");
  const start = api.starts[0]?.body;
  if (!start) throw new Error("Missing upload metadata");
  page.once("dialog", (dialog) => dialog.accept());
  await page.goto(`/documents/${start.documentId}/revisions/${start.revisionId}/upload`);
  await expect(page.getByRole("heading", { name: "Resume upload", exact: true })).toBeVisible();
  await choose(page, Buffer.from("Different content"));
  await expect(page.getByRole("alert")).toContainText("Select the same file");
  await choose(page, smallFile, "renamed-copy.pdf");
  await expect(page.getByRole("button", { name: "Change file", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Continue upload", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Upload complete.");
  expect(api.starts).toHaveLength(1);
  expect(api.chunks).toHaveLength(2);
  expect(api.chunks[1]?.operation).toBe(api.chunks[0]?.operation);
  expect(api.revisions.get(start.revisionId)?.fileName).toBe("record.pdf");
});

test("unknown metadata outcome stays pinned through MFA recovery and subsequent rejection", async ({
  page,
}) => {
  const api = await installDocumentUploadsApi(page);
  await newUpload(page);
  api.loseStart();
  await page.getByRole("button", { name: "Start upload", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Upload paused.");
  const original = api.starts[0];
  api.identity.expireMfa();
  api.rejectCommand("mfa_required");
  await page.getByRole("button", { name: "Retry upload", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByText("Employee · EMP-001", { exact: true })).toHaveCount(0);
  expect(api.starts).toHaveLength(2);
  api.rejectCommand("stale_version");
  await page.getByRole("button", { name: "Retry upload", exact: true }).click();
  await expect(page.getByRole("button", { name: "Reload review", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Retry upload", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Upload complete.");
  expect(api.starts).toEqual([original, original, original, original]);
  expect(api.chunks).toHaveLength(1);
  await expect(page.getByText("PRIVATE upload diagnostic", { exact: true })).toHaveCount(0);
});

test("leaving a company cancels page ownership and hides a late upload result", async ({
  page,
}) => {
  const api = await installDocumentUploadsApi(page);
  await newUpload(page);
  const held = api.holdChunk();
  try {
    await page.getByRole("button", { name: "Start upload", exact: true }).click();
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Stay on this page", exact: true })
      .click();
    await expect(page.getByRole("button", { name: "Pause upload", exact: true })).toBeVisible();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("table", { name: "Employees", exact: true })).toContainText(
      "South employee",
    );
    held.release();
    await expect(page.getByText("Signed employment record", { exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Retry upload", exact: true })).toHaveCount(0);
    expect(api.starts).toHaveLength(1);
  } finally {
    held.release();
  }
});

test("read-only grants do not expose upload controls or permit direct upload routes", async ({
  page,
}) => {
  const api = await installDocumentUploadsApi(page, [
    "company.read",
    "documents.read",
    "people.profile.read",
  ]);
  await page.goto(folder);
  await expect(page.getByRole("heading", { name: "North employee", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Upload document", exact: true })).toHaveCount(0);
  await page.goto(`${folder}/upload`);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("button", { name: "Choose file", exact: true })).toHaveCount(0);
  expect(api.starts).toHaveLength(0);
});

test("invalid fields stay editable and an obsolete document version requires a fresh review", async ({
  page,
}) => {
  const api = await installDocumentUploadsApi(page);
  await page.goto(`/documents/${documentHeaderId}/upload`);
  await choose(page);
  await expect(page.getByRole("button", { name: "Change file", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Start upload", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("provide a reason");
  expect(api.starts).toHaveLength(0);
  api.setDocumentVersion(documentHeaderId, 3);
  await page.getByLabel("Reason", { exact: true }).fill("Renew the signed copy");
  await page.getByRole("button", { name: "Start upload", exact: true }).click();
  await expect(page.getByRole("button", { name: "Start upload", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Renew current record");
  await choose(page);
  await expect(page.getByRole("button", { name: "Change file", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Start upload", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Upload complete.");
  expect(api.starts.map((request) => request.body.expectedDocumentVersion)).toEqual([2, 3]);
});

test("upload form supports narrow layouts, both themes and Indonesian copy", async ({ page }) => {
  await installDocumentUploadsApi(page);
  await newUpload(page);
  await page.screenshot({
    path: "../../.work/dashboard-upload-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("heading", { name: "Upload dokumen", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Ganti file", exact: true })).toBeEnabled();
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-upload-dark-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Mulai upload", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Upload selesai. File harus lolos validasi");
});
