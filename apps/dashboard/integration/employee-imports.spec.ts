import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import type { EmployeeImportStartDto } from "../src/features/people/data/models/employee-import-start-dto";
import { createAdminCompany } from "./fixtures/admin-company";

test("real API CSV preview and cancellation retain their original commands after lost responses", async ({
  page,
  context,
}) => {
  const { company } = await createAdminCompany(
    page,
    context,
    "browser-import-admin@example.invalid",
    "IMPORTBROWSER",
    "Browser Imports",
  );
  const importPath = `/api/v1/companies/${company}/employee-imports`;
  const importCsv =
    "\ufeffemployee_number,legal_name,nationality,start_date,contract\nIMP01,Import fixture employee,ID,2026-01-01,PERMANENT\nIMP02,Invalid import date,ID,invalid,PERMANENT";
  const importStarts: { operation: string | undefined; body: EmployeeImportStartDto }[] = [];
  await page.route(`**${importPath}`, async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    const body = route.request().postDataJSON() as EmployeeImportStartDto;
    expect(body.csv).toBe(importCsv);
    importStarts.push({ operation: route.request().headers()["idempotency-key"], body });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ id: body.id, version: 0 });
    if (importStarts.length === 1) return route.abort("connectionfailed");
    return route.fulfill({ response });
  });
  await page.getByRole("link", { name: "Import karyawan", exact: true }).click();
  await page.getByRole("link", { name: "Import CSV", exact: true }).click();
  const templateDownload = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download template", exact: true }).click();
  const template = await templateDownload;
  expect(template.suggestedFilename()).toBe("employee-import-template.csv");
  const csvTemplatePath = await template.path();
  if (!csvTemplatePath) throw new Error("Expected CSV download");
  expect(await readFile(csvTemplatePath, "utf8")).toContain(
    "employee_number,legal_name,nationality,start_date,contract",
  );
  const selectingCsv = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: "Pilih CSV", exact: true }).click();
  await (await selectingCsv).setFiles({
    name: "browser-employees.csv",
    mimeType: "text/csv",
    buffer: Buffer.from(importCsv),
  });
  await page.getByLabel("Alasan", { exact: true }).fill("Browser import preview fixture");
  await page.getByRole("button", { name: "Siapkan preview", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Hasil belum terkonfirmasi.");
  await page.getByRole("button", { name: "Ulangi permintaan awal", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Preview diantrekan.");
  expect(importStarts).toHaveLength(2);
  expect(importStarts[1]).toEqual(importStarts[0]);
  const importId = importStarts[0]?.body.id;
  if (!importId) throw new Error("Expected import receipt");
  await page.unroute(`**${importPath}`);
  await page.getByRole("link", { name: "Buka import", exact: true }).click();
  await expect(page.getByRole("region", { name: "Ringkasan", exact: true })).toContainText(
    "Menyiapkan preview",
  );
  await expect(page.getByRole("region", { name: "Baris", exact: true })).toContainText("Tertunda");
  await page.getByRole("tab", { name: "Baris", exact: true }).click();
  await expect(page.getByRole("table", { name: "Baris", exact: true })).toContainText(
    "Import fixture employee",
  );
  await page.getByRole("button", { name: "Lihat baris: 1 · IMP01", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Detail baris", exact: true })).toContainText(
    "2026-01-01",
  );
  await page.getByRole("button", { name: "Tutup", exact: true }).click();
  await page.getByRole("button", { name: "Lihat baris: 2 · IMP02", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Detail baris", exact: true })).toContainText(
    "Usulan tidak dapat dibuat dari baris ini.",
  );
  await page.getByRole("button", { name: "Tutup", exact: true }).click();
  await page.getByRole("tab", { name: "Percobaan", exact: true }).click();
  await expect(page.getByRole("table", { name: "Percobaan", exact: true })).toContainText(
    "Preview",
  );
  const queuedImport = await (await context.request.get(`${importPath}/${importId}`)).json();
  expect(queuedImport).toMatchObject({
    batch: { id: importId, version: 0, status: "PREVIEWING" },
    counts: { PENDING: 2 },
    jobStatus: "QUEUED",
    cancellationRequested: false,
    availableActions: ["cancel"],
  });
  await page.getByRole("link", { name: "Batalkan import", exact: true }).click();
  await page.getByLabel("Alasan", { exact: true }).fill("Browser import cancellation");
  const importCancellations: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**${importPath}/${importId}/cancel`, async (route) => {
    importCancellations.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ id: importId, version: 1 });
    if (importCancellations.length === 1) return route.abort("connectionfailed");
    return route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Batalkan import", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Hasil belum terkonfirmasi.");
  await page.getByRole("button", { name: "Ulangi permintaan awal", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Pembatalan diminta.");
  expect(importCancellations).toHaveLength(2);
  expect(importCancellations[1]).toEqual(importCancellations[0]);
  await page.unroute(`**${importPath}/${importId}/cancel`);
  await page.getByRole("link", { name: "Buka import", exact: true }).click();
  await expect(page.getByRole("link", { name: "Batalkan import", exact: true })).toHaveCount(0);
  expect(await (await context.request.get(`${importPath}/${importId}`)).json()).toMatchObject({
    batch: { version: 1, status: "PREVIEWING" },
    jobStatus: "QUEUED",
    cancellationRequested: true,
    availableActions: [],
  });
});
