import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { documentBytes } from "../tests/fixtures/documents";
import { createAdminCompany } from "./fixtures/admin-company";

test("real document metadata upload and correction preserve business validity, history and original receipt", async ({
  page,
  context,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-metadata-admin@example.invalid",
    "METADATA_BROWSER",
    "Document Metadata Browser",
  );
  const base = `/api/v1/companies/${company}`,
    employee = randomUUID();
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const created = await context.request.post(`${base}/employees`, {
    headers: headers(),
    data: {
      id: employee,
      employeeNumber: "METADATA-001",
      person: { id: randomUUID(), legalName: "Metadata Fixture Employee", nationality: "AU" },
      terms: {
        effectiveFrom: "2026-01-01",
        startDate: "2026-01-01",
        status: "ACTIVE",
        contract: "PERMANENT",
      },
      reason: "Document metadata acceptance",
    },
  });
  expect(created.status()).toBe(200);
  await page.goto(`/documents/employees/${employee}/upload?company=${company}`);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByLabel("Judul", { exact: true }).fill("Historical passport");
  await page.getByLabel("Klasifikasi", { exact: true }).selectOption("PERSONAL");
  await page.getByLabel("Kategori", { exact: true }).selectOption("PASSPORT");
  await page.getByLabel("Nomor dokumen", { exact: true }).fill("PASSPORT-FIXTURE");
  await page.getByLabel("Negara penerbit", { exact: true }).fill("AU");
  await page.getByLabel("Tanggal terbit", { exact: true }).fill("2010-01-01");
  await page.getByLabel("Berlaku sampai", { exact: true }).fill("2020-01-01");
  await page.getByLabel("Alasan", { exact: true }).fill("Record historical identity evidence");
  const chooser = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: "Pilih file", exact: true }).click();
  await (await chooser).setFiles({
    name: "passport.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from(documentBytes()),
  });
  // A definite initial rejection remains editable and does not create a document.
  await page.getByLabel("Negara penerbit", { exact: true }).fill("ZZ");
  const rejectedResponse = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().endsWith(`${base}/documents/uploads`),
  );
  await page.getByRole("button", { name: "Mulai upload", exact: true }).click();
  const rejected = await rejectedResponse;
  expect(rejected.status()).toBe(422);
  await expect(page.getByRole("alert")).toContainText(
    "Periksa kategori, nomor dokumen, kode negara, dan tanggal.",
  );
  await expect(page.getByRole("button", { name: "Mulai upload", exact: true })).toBeEnabled();
  const rejectedDocument = rejected.request().postDataJSON() as { documentId: string };
  expect(
    (await context.request.get(`${base}/documents/${rejectedDocument.documentId}`)).status(),
  ).toBe(404);
  await page.getByLabel("Negara penerbit", { exact: true }).fill("AU");
  const uploadResponse = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().endsWith(`${base}/documents/uploads`),
  );
  await page.getByRole("button", { name: "Mulai upload", exact: true }).click();
  const uploaded = await uploadResponse;
  expect(uploaded.status()).toBe(200);
  const initial = uploaded.request().postDataJSON() as {
    documentId: string;
    revisionId: string;
    metadata: unknown;
  };
  expect(initial.metadata).toEqual({
    category: "PASSPORT",
    identityNumber: "PASSPORT-FIXTURE",
    issuingCountry: "AU",
    issuedOn: "2010-01-01",
    validUntil: "2020-01-01",
  });
  // This fixture has no object store. The real metadata/header is already committed before reservation fails.
  await expect(page.getByRole("status")).toContainText("Upload terhenti.");
  const document = initial.documentId,
    revision = initial.revisionId;
  const originalRevision = await (
    await context.request.get(`${base}/documents/revisions/${revision}`)
  ).json();
  const oldHeader = await (await context.request.get(`${base}/documents/${document}`)).json();
  expect(oldHeader).toMatchObject({ metadataVersion: 0, metadata: initial.metadata, version: 1 });
  await page.getByRole("link", { name: "Kembali", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Tinggalkan halaman", exact: true })
    .click();
  await page.goto(`/documents/${document}/metadata/edit?company=${company}`);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByLabel("Berlaku sampai", { exact: true }).fill("2030-01-01");
  await page.getByLabel("Alasan perubahan", { exact: true }).fill("Correct printed validity date");
  const commands: { key: string; body: unknown }[] = [];
  await page.route(`**${base}/documents/${document}/metadata`, async (route) => {
    commands.push({
      key: route.request().headers()["idempotency-key"] ?? "",
      body: route.request().postDataJSON(),
    });
    if (commands.length === 1) {
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      await response.dispose();
      await route.abort("failed");
    } else await route.continue();
  });
  await page.getByRole("button", { name: "Simpan rincian", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Hasil belum terkonfirmasi.");
  const later = await context.request.put(`${base}/documents/${document}/metadata`, {
    headers: headers(),
    data: {
      expectedVersion: 2,
      metadata: { ...oldHeader.metadata, validUntil: "2035-01-01" },
      reason: "Independent later correction",
    },
  });
  expect(later.status()).toBe(200);
  await page.getByRole("button", { name: "Periksa permintaan semula", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Rincian dokumen disimpan.");
  expect(commands).toHaveLength(2);
  expect(commands[0]).toEqual(commands[1]);
  await page.getByRole("link", { name: "Lihat dokumen", exact: true }).click();
  await page.getByRole("link", { name: "Riwayat rincian dokumen", exact: true }).click();
  const table = page.getByRole("table", { name: "Riwayat rincian dokumen", exact: true });
  await expect(table).toContainText("2020-01-01");
  await expect(table).toContainText("2030-01-01");
  await expect(table).toContainText("2035-01-01");
  const history = await (
    await context.request.get(`${base}/documents/${document}/metadata-history`)
  ).json();
  expect(history.items.map((row: { version: number }) => row.version)).toEqual([0, 1, 2]);
  const finalRevision = await (
    await context.request.get(`${base}/documents/revisions/${revision}`)
  ).json();
  expect(finalRevision).toEqual(originalRevision);
  const current = await (await context.request.get(`${base}/documents/${document}`)).json();
  expect(current).toMatchObject({
    version: 3,
    metadataVersion: 2,
    revisionCount: 1,
    metadata: { validUntil: "2035-01-01" },
  });
});
