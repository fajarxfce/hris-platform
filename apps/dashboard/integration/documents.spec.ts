import { createHash, randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { documentBytes } from "../tests/fixtures/documents";
import { createAdminCompany } from "./fixtures/admin-company";

test("real document employee discovery, upload metadata and cancellation remain company-scoped", async ({
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
    employee = randomUUID(),
    document = randomUUID(),
    revision = randomUUID();
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
  const started = await context.request.post(`${base}/documents/uploads`, {
    headers: headers(),
    data: {
      documentId: document,
      revisionId: revision,
      employmentId: employee,
      title: "Employment record",
      classification: "HR_ONLY",
      expectedDocumentVersion: 0,
      fileName: "record.pdf",
      mediaType: "application/pdf",
      size: bytes.length,
      sha256: createHash("sha256").update(bytes).digest("hex"),
      reason: "Administrative record",
    },
  });
  expect(started.status()).toBe(200);
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
