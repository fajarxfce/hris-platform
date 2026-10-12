import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { installDocumentsApi } from "./documents-api";
import {
  documentBytes,
  documentEmployeeId,
  documentHeaderId,
  documentId,
  documentRevisionId,
} from "./fixtures/documents";
import { companyIds } from "./identity-api";

const revisionPath = `/documents/${documentHeaderId}/revisions/${documentRevisionId}`;
test.use({ timezoneId: "Pacific/Honolulu" });

test("employee documents retain published revision identity and download authenticated content", async ({
  page,
}) => {
  const api = await installDocumentsApi(page, { replacement: true });
  await page.goto("/documents");
  await expect(page.getByRole("link", { name: "Documents", exact: true })).toBeVisible();
  await expect(page.getByRole("table", { name: "Employees", exact: true })).toContainText(
    "North employee",
  );
  await page
    .getByRole("button", { name: "Open documents: North employee (EMP-001)", exact: true })
    .click();
  await expect(page).toHaveURL(
    new RegExp(`/documents/employees/${documentEmployeeId}\\?company=${companyIds[0]}`),
  );
  await page
    .getByRole("button", { name: "Open document: Employment certificate", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Employment certificate", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("table", { name: "Revisions", exact: true })).toContainText(
    "Uploading",
  );
  await page.screenshot({ path: "../../.work/dashboard-documents-light.png", fullPage: true });
  await page.getByRole("link", { name: "View published revision", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`${revisionPath}\\?company=${companyIds[0]}`));
  await expect(page.getByText("certificate.pdf", { exact: true })).toBeVisible();
  await expect(
    page
      .getByRole("region", { name: "Revision 1", exact: true })
      .getByText("Oct 1, 2026, 7:00 AM (Asia/Jakarta)", { exact: true }),
  ).toBeVisible();
  const saved = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download", exact: true }).click();
  const download = await saved;
  expect(download.suggestedFilename()).toBe("certificate.pdf");
  const path = await download.path();
  if (!path) throw new Error("Expected downloaded fixture");
  expect(await readFile(path)).toEqual(Buffer.from(documentBytes()));
  await expect(page.getByRole("status").filter({ hasText: "Download started." })).toBeVisible();
  expect(
    api.reads.filter((url) => url.pathname.endsWith(`/revisions/${documentRevisionId}`)),
  ).toHaveLength(2);
  expect(api.reads.every((url) => url.pathname.includes(companyIds[0]))).toBe(true);
  expect(api.identity.unhandled).toHaveLength(0);
});

test("employee search has bounded pages and sends filters only on submission", async ({ page }) => {
  const api = await installDocumentsApi(page, { employeeCount: 21 });
  await page.goto("/documents");
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(2);
  expect(api.reads.at(-1)?.searchParams.get("after")).toBe("EMP-020");
  const count = api.reads.length;
  await page.getByLabel("Employee name or number", { exact: true }).fill(" EMP-001 ");
  expect(api.reads).toHaveLength(count);
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(page.getByRole("table")).toContainText("EMP-001");
  expect(api.reads.at(-1)?.searchParams.get("query")).toBe("EMP-001");
  expect(api.reads.at(-1)?.searchParams.has("after")).toBe(false);
  await expect(page.getByRole("button", { name: "First page", exact: true })).toBeDisabled();
  await page.getByLabel("Employee name or number", { exact: true }).fill("Missing employee");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(page.getByText("No employees match this search.", { exact: true })).toBeVisible();
});

test("revision details support Indonesian, dark mode and a narrow viewport without enabling unfinished files", async ({
  page,
}) => {
  const api = await installDocumentsApi(page, { replacement: true });
  await page.goto(`/documents/${documentHeaderId}/revisions/${documentId(32)}`);
  await expect(page.getByText("replacement.pdf", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Download", exact: true })).toHaveCount(0);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("heading", { name: "Detail revisi", exact: true })).toBeVisible();
  await expect(page.getByText("Mengunggah", { exact: true })).toBeVisible();
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-documents-dark-mobile.png",
    fullPage: true,
  });
  expect(api.reads.some((url) => url.pathname.endsWith("/content"))).toBe(false);
  expect(api.identity.unhandled).toHaveLength(0);
});

test("known content denial stays hidden through a pending and failed refresh", async ({ page }) => {
  const api = await installDocumentsApi(page);
  await page.goto(revisionPath);
  await expect(page.getByText("certificate.pdf", { exact: true })).toBeVisible();
  api.rejectContent("document_access_denied");
  await page.getByRole("button", { name: "Download", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "You no longer have access to this document.",
  );
  await expect(page.getByText("certificate.pdf", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Download", exact: true })).toHaveCount(0);
  const held = api.holdRead();
  api.rejectRead("request_failed");
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.started;
    await expect(page.getByText("certificate.pdf", { exact: true })).toHaveCount(0);
    held.release();
    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page.getByText("certificate.pdf", { exact: true })).toHaveCount(0);
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(page.getByText("certificate.pdf", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Download", exact: true })).toBeEnabled();
    expect(api.reads.filter((url) => url.pathname.endsWith("/content"))).toHaveLength(1);
    await expect(page.getByText("PRIVATE TECHNICAL ERROR", { exact: true })).toHaveCount(0);
  } finally {
    held.release();
  }
});

test("MFA recovery requires fresh metadata and never repeats the download", async ({ page }) => {
  const api = await installDocumentsApi(page);
  await page.goto(revisionPath);
  await expect(page.getByRole("button", { name: "Download", exact: true })).toBeEnabled();
  api.identity.expireMfa();
  api.rejectContent("mfa_required");
  await page.getByRole("button", { name: "Download", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByText("certificate.pdf", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("button", { name: "Download", exact: true })).toBeEnabled();
  expect(api.reads.filter((url) => url.pathname.endsWith("/content"))).toHaveLength(1);
});

test("company departure cancels an in-flight download and discards the previous resource route", async ({
  page,
}) => {
  const api = await installDocumentsApi(page),
    downloads: string[] = [];
  page.on("download", (download) => downloads.push(download.suggestedFilename()));
  await page.goto(revisionPath);
  const held = api.holdContent();
  try {
    await page.getByRole("button", { name: "Download", exact: true }).click();
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Employees", exact: true })).toContainText(
      "South employee",
    );
    held.release();
    await expect(page).toHaveURL(`/documents?company=${companyIds[1]}`);
    await expect(page.getByText("certificate.pdf", { exact: true })).toHaveCount(0);
    expect(
      api.reads
        .filter((url) => url.pathname.includes(companyIds[1]))
        .every((url) => url.pathname.endsWith("/documents/employees")),
    ).toBe(true);
    expect(downloads).toHaveLength(0);
  } finally {
    held.release();
  }
});

test("retired content cannot be downloaded from previously ready metadata", async ({ page }) => {
  const api = await installDocumentsApi(page);
  await page.goto(revisionPath);
  await expect(page.getByRole("button", { name: "Download", exact: true })).toBeEnabled();
  api.retire();
  await page.getByRole("button", { name: "Download", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "This revision is not available for download.",
  );
  await expect(page.getByText("certificate.pdf", { exact: true })).toHaveCount(0);
  expect(api.reads.some((url) => url.pathname.endsWith("/content"))).toBe(false);
});

test("foreign company deep links discard resource identifiers before acquisition", async ({
  page,
}) => {
  const api = await installDocumentsApi(page);
  await page.goto(`${revisionPath}?company=${companyIds[1]}`);
  await expect(page.getByRole("table", { name: "Employees", exact: true })).toContainText(
    "North employee",
  );
  expect(
    api.reads.every(
      (url) => url.pathname === `/api/v1/companies/${companyIds[0]}/documents/employees`,
    ),
  ).toBe(true);
});

for (const permission of ["documents.read", "people.profile.read", "documents.self.read"]) {
  test(`the ${permission} grant alone cannot open administrative documents`, async ({ page }) => {
    const api = await installDocumentsApi(page, { permissions: ["company.read", permission] });
    await page.goto(revisionPath);
    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page.getByRole("link", { name: "Documents", exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Download", exact: true })).toHaveCount(0);
    expect(api.reads).toHaveLength(0);
  });
}
