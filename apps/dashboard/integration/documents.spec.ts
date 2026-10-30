import { createHash, randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { documentBytes } from "../tests/fixtures/documents";
import { createAdminCompany } from "./fixtures/admin-company";

test("real browser upload, reserved part review and cancellation remain company-scoped", async ({
  page,
  context,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-document-admin@example.invalid",
    "BROWSER_DOCUMENT",
    "Browser Documents",
  );
  const base = `/api/v1/companies/${company}`,
    employee = randomUUID();
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const enrolled = await context.request.post(`${base}/employees`, {
    headers: headers(),
    data: {
      id: employee,
      employeeNumber: "DOCUMENT-001",
      person: { id: randomUUID(), legalName: "Document Fixture Employee", nationality: "ID" },
      terms: {
        effectiveFrom: "2026-01-01",
        startDate: "2026-01-01",
        status: "ACTIVE",
        contract: "PERMANENT",
      },
      reason: "Document browser acceptance",
    },
  });
  expect(enrolled.status()).toBe(200);
  await page.getByRole("link", { name: "Dokumen", exact: true }).click();
  await page
    .getByRole("button", {
      name: "Buka dokumen: Document Fixture Employee (DOCUMENT-001)",
      exact: true,
    })
    .click();
  await expect(
    page.getByText("Belum ada dokumen untuk karyawan ini.", { exact: true }),
  ).toBeVisible();
  const bytes = documentBytes();
  await page.getByRole("link", { name: "Upload dokumen", exact: true }).click();
  await page.getByLabel("Judul", { exact: true }).fill("Employment record");
  await page.getByLabel("Klasifikasi", { exact: true }).selectOption("HR_ONLY");
  await page.getByLabel("Alasan", { exact: true }).fill("Administrative record");
  const chooser = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: "Pilih file", exact: true }).click();
  await (await chooser).setFiles({
    name: "record.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from(bytes),
  });
  await expect(page.getByRole("button", { name: "Ganti file", exact: true })).toBeEnabled();
  const parts: { status: number; body: unknown }[] = [];
  await page.route(`**${base}/documents/revisions/*/chunks`, async (route) => {
    // Capture the real server response before Chromium releases the failed upload stream.
    const response = await route.fetch();
    try {
      parts.push({ status: response.status(), body: await response.json() });
      await route.fulfill({ response });
    } finally {
      await response.dispose();
    }
  });
  const metadataResponse = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().endsWith(`${base}/documents/uploads`),
  );
  const partResponse = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().includes(`${base}/documents/revisions/`) &&
      response.url().endsWith("/chunks"),
  );
  await page.getByRole("button", { name: "Mulai upload", exact: true }).click();
  const started = await metadataResponse;
  expect(started.status()).toBe(200);
  const command = started.request().postDataJSON() as {
    documentId: string;
    revisionId: string;
    sha256: string;
  };
  const document = command.documentId,
    revision = command.revisionId;
  expect(command.sha256).toBe(createHash("sha256").update(bytes).digest("hex"));
  const header = await context.request.get(`${base}/documents/${document}`);
  expect(await header.json()).toMatchObject({
    employmentId: employee,
    revisionCount: 1,
    currentRevisionId: null,
  });
  const part = await partResponse;
  // This isolated API has no object storage. Reservation and its recovery metadata are real.
  expect(part.status()).toBe(503);
  expect(parts).toEqual([
    { status: 503, body: expect.objectContaining({ code: "object_storage_not_configured" }) },
  ]);
  await expect(page.getByRole("status")).toContainText("Upload terhenti.");
  const reserved = await context.request.get(`${base}/documents/revisions/${revision}`);
  expect(await reserved.json()).toMatchObject({
    uploadContinuation: {
      operationId: part.request().headers()["idempotency-key"],
      offset: 0,
      size: bytes.length,
      attempts: 1,
    },
  });
  await page.getByRole("link", { name: "Kembali", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Tinggalkan halaman", exact: true })
    .click();
  await page.getByRole("button", { name: "Muat ulang", exact: true }).click();
  await page.getByRole("button", { name: "Buka dokumen: Employment record", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Employment record", exact: true })).toBeVisible();
  await expect(page.getByText("Khusus HR", { exact: true })).toBeVisible();
  await expect(page.getByText("Belum ada revisi terbit", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Buka revisi: 1: record.pdf", exact: true }).click();
  await expect(page.getByText("record.pdf", { exact: true })).toBeVisible();
  await expect(page.getByText("Mengunggah", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Download", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Validasi file", exact: true })).toHaveCount(0);
  const commands: { operation: string; body: unknown }[] = [];
  await page.route(`**${base}/documents/revisions/${revision}/cancel`, async (route) => {
    commands.push({
      operation: route.request().headers()["idempotency-key"] ?? "",
      body: route.request().postDataJSON(),
    });
    if (commands.length === 1) {
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      await response.dispose();
      await route.abort("failed");
    } else await route.continue();
  });
  await page.getByRole("link", { name: "Batalkan upload", exact: true }).click();
  await page.getByLabel("Alasan", { exact: true }).fill("Finish browser metadata acceptance");
  await page.getByRole("button", { name: "Konfirmasi", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Respons belum terkonfirmasi.");
  await page.getByRole("button", { name: "Ulangi permintaan", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Upload dibatalkan.");
  expect(commands).toHaveLength(2);
  expect(commands[0]).toEqual(commands[1]);
  expect(commands[0]?.body).toEqual({
    expectedVersion: 0,
    reason: "Finish browser metadata acceptance",
  });
  await page.getByRole("link", { name: "Lihat revisi", exact: true }).click();
  await expect(page.getByText("Dibatalkan", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Batalkan upload", exact: true })).toHaveCount(0);
  const otherCompany = await context.request.post("/api/v1/companies", {
    headers: headers(),
    data: {
      code: "BROWSER_DOCUMENT_OTHER",
      name: "Other Document Company",
      timezone: "Asia/Jakarta",
    },
  });
  expect(otherCompany.status()).toBe(200);
  const otherId = ((await otherCompany.json()) as { id: string }).id;
  const foreign = await context.request.get(
    `/api/v1/companies/${otherId}/documents/revisions/${revision}`,
  );
  expect(foreign.status()).toBe(404);
  expect(await foreign.json()).toMatchObject({ code: "document_revision_not_found" });
  const directory = await context.request.get(
    `/api/v1/companies/${otherId}/documents/employees?employmentId=${employee}`,
  );
  expect(directory.status()).toBe(200);
  expect(await directory.json()).toMatchObject({ employees: { items: [], nextCursor: null } });
});
