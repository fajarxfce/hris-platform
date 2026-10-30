import { expect, test } from "@playwright/test";
import { installDocumentMetadataApi } from "./document-metadata-api";
import { documentHeaderId } from "./fixtures/documents";

const path = `/documents/${documentHeaderId}`;
test.use({ timezoneId: "Pacific/Honolulu" });

test("metadata correction recovers its original lost receipt and retains immutable history", async ({
  page,
}) => {
  const api = await installDocumentMetadataApi(page);
  await page.goto(path);
  await expect(page.getByText("P-0", { exact: true })).toBeVisible();
  await expect(page.getByText("Jan 1, 2020", { exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Edit document details", exact: true }).click();
  await page.getByLabel("Document number", { exact: true }).fill("P-456");
  await page.getByLabel("Valid until", { exact: true }).fill("2030-01-01");
  await page.getByLabel("Reason for change", { exact: true }).fill("Correct the validity date");
  api.loseNext();
  await page.getByRole("button", { name: "Save details", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is not confirmed.");
  await expect(page.getByRole("button", { name: "Save details", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Check the original request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Document details saved.");
  expect(api.commands).toHaveLength(2);
  expect(api.commands[0]).toEqual(api.commands[1]);
  expect(api.history).toHaveLength(2);
  await page.getByRole("link", { name: "View document", exact: true }).click();
  await expect(page.getByText("P-456", { exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Document details history", exact: true }).click();
  const table = page.getByRole("table", { name: "Document details history", exact: true });
  await expect(table).toContainText("P-0");
  await expect(table).toContainText("P-456");
  await expect(table).toContainText("Existing record");
  await expect(table).toContainText("Correct the validity date");
  expect(api.identity.unhandled).toHaveLength(0);
});

test("invalid dates retain the draft and dirty departure requires an explicit choice", async ({
  page,
}) => {
  const api = await installDocumentMetadataApi(page);
  await page.goto(`${path}/metadata/edit`);
  await page.getByLabel("Valid until", { exact: true }).fill("1900-01-01");
  await page.getByLabel("Reason for change", { exact: true }).fill("Review dates");
  await page.getByRole("button", { name: "Save details", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Review the category, document number, country code and dates.",
  );
  expect(api.commands).toHaveLength(0);
  await page.getByRole("link", { name: "Back to document", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await expect(page.getByLabel("Valid until", { exact: true })).toHaveValue("1900-01-01");
});

test("read access exposes history without offering metadata management", async ({ page }) => {
  const api = await installDocumentMetadataApi(page, { readOnly: true });
  await page.goto(path);
  await expect(page.getByRole("link", { name: "Edit document details", exact: true })).toHaveCount(
    0,
  );
  await page.getByRole("link", { name: "Document details history", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Document details history", exact: true }),
  ).toContainText("P-0");
  await page.goto(`${path}/metadata/edit`);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByLabel("Document number", { exact: true })).toHaveCount(0);
  expect(api.commands).toHaveLength(0);
});

test("legacy API details stay readable without unsupported metadata actions", async ({ page }) => {
  const api = await installDocumentMetadataApi(page, { unsupported: true });
  await page.goto(path);
  await expect(
    page.getByRole("heading", { name: "Employment certificate", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "Edit document details", exact: true })).toHaveCount(
    0,
  );
  await expect(
    page.getByRole("link", { name: "Document details history", exact: true }),
  ).toHaveCount(0);
  await page.goto(`${path}/metadata/edit`);
  await expect(page.getByRole("alert")).toContainText(
    "Document details cannot be edited here yet.",
  );
  expect(api.commands).toHaveLength(0);
});

test("Indonesian metadata form remains usable at a narrow dark viewport", async ({ page }) => {
  await installDocumentMetadataApi(page);
  await page.goto(`${path}/metadata/edit`);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("heading", { name: "Ubah rincian dokumen", exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Nomor dokumen", { exact: true })).toHaveValue("P-0");
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/document-business-metadata/dashboard-metadata-dark-mobile.png",
    fullPage: true,
  });
});
