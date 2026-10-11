import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { companyDate } from "../src/core/presentation/dates/company-date";
import { createAdminCompany } from "./fixtures/admin-company";

test("real API company policy recovers receipts and maintenance while scheduled jobs retain cancellation", async ({
  page,
  context,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-client-policy-admin@example.invalid",
    "CLIENTPOLICYBROWSER",
    "Browser Client Policy",
  );
  await page.getByRole("link", { name: "Policy client", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText(
    "Belum ada konfigurasi perusahaan yang disimpan.",
  );
  const policyPath = `/api/v1/companies/${company}/settings/client-policy`;
  await page.getByRole("link", { name: "Edit policy terbaru", exact: true }).click();
  await page.getByRole("checkbox", { name: "Pengeluaran", exact: true }).uncheck();
  await page.getByLabel("Android", { exact: true }).fill("1");
  await page.getByLabel("Alasan", { exact: true }).fill("Browser policy revision 0");
  await page.getByRole("button", { name: "Simpan policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy tersimpan.");
  await page.getByRole("link", { name: "Lihat revisi tersimpan", exact: true }).click();
  await page.getByRole("link", { name: "Edit policy terbaru", exact: true }).click();
  await page.getByLabel("Aktivasi", { exact: true }).selectOption("SCHEDULED");
  await page
    .getByLabel("Mulai berlaku (UTC)", { exact: true })
    .fill(new Date(Date.now() + 600_000).toISOString());
  await page.getByRole("checkbox", { name: "Pengeluaran", exact: true }).check();
  await page.getByRole("checkbox", { name: "Payroll", exact: true }).uncheck();
  await page.getByLabel("Android", { exact: true }).fill("2");
  await page.getByLabel("Alasan", { exact: true }).fill("Browser policy revision 1");
  const policyWrites: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**${policyPath}`, async (route) => {
    if (route.request().method() !== "PUT") return route.fallback();
    policyWrites.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ id: company, version: 1 });
    if (policyWrites.length === 1) return route.abort("connectionfailed");
    return route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Simpan policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Hasil penyimpanan belum terkonfirmasi.");
  await page.getByRole("button", { name: "Ulangi penyimpanan awal", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy tersimpan.");
  expect(policyWrites).toHaveLength(2);
  expect(policyWrites[1]).toEqual(policyWrites[0]);
  await page.unroute(`**${policyPath}`);
  await page.getByRole("link", { name: "Lihat revisi tersimpan", exact: true }).click();
  const effectivePolicy = page.getByRole("region", { name: "Policy efektif", exact: true });
  await expect(
    effectivePolicy
      .locator(".app-property-row")
      .filter({ has: page.getByText("Revisi", { exact: true }) }),
  ).toContainText("0");
  await expect(
    effectivePolicy.locator(".app-property-row").filter({ hasText: "Revisi konfigurasi terbaru" }),
  ).toContainText("1");
  const policyConfiguration = page.getByRole("region", { name: "Revisi konfigurasi", exact: true });
  await expect(policyConfiguration).toContainText("Browser policy revision 1");
  const modules = page.getByRole("table", { name: "Modul", exact: true });
  await expect(modules.getByRole("row").filter({ hasText: "Pengeluaran" })).toContainText(
    "Nonaktif",
  );
  await expect(modules.getByRole("row").filter({ hasText: "Payroll" })).toContainText("Aktif");
  await page.getByLabel("Revisi", { exact: true }).fill("0");
  await page.getByRole("button", { name: "Lihat revisi", exact: true }).click();
  await expect(policyConfiguration).toContainText("Browser policy revision 0");
  for (const [maintenance, build, code, status] of [
    [true, "0", "company_maintenance", 503],
    [false, "2", "client_update_required", 403],
    [false, "0", null, 200],
  ] as const) {
    await page.getByRole("link", { name: "Edit policy terbaru", exact: true }).click();
    await page.getByLabel("Aktivasi", { exact: true }).selectOption("IMMEDIATE");
    await page.getByLabel("Web", { exact: true }).fill(build);
    await page
      .getByRole("checkbox", { name: "Jadwalkan maintenance", exact: true })
      .setChecked(maintenance);
    if (maintenance) {
      await page
        .getByLabel("Mulai maintenance (UTC)", { exact: true })
        .fill(new Date(Date.now() - 60_000).toISOString());
      await page
        .getByLabel("Akhir maintenance (UTC)", { exact: true })
        .fill(new Date(Date.now() + 600_000).toISOString());
    }
    await page
      .getByLabel("Alasan", { exact: true })
      .fill(`Browser policy recovery ${build} ${maintenance}`);
    await page.getByRole("button", { name: "Simpan policy", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Policy tersimpan.");
    await page.getByRole("link", { name: "Lihat revisi tersimpan", exact: true }).click();
    await expect(policyConfiguration).toContainText("Browser policy recovery");
    const business = await context.request.get(
      `/api/v1/companies/${company}/employees?asOf=${companyDate("Asia/Jakarta", new Date())}`,
      {
        headers: { "X-HRIS-Client-Platform": "WEB", "X-HRIS-Client-Build": "1" },
      },
    );
    expect(business.status()).toBe(status);
    if (code) expect(await business.json()).toMatchObject({ code });
  }
  const announcementPath = `/api/v1/companies/${company}/announcements/${randomUUID()}`;
  const draft = await context.request.put(announcementPath, {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: {
      title: "Browser job fixture",
      body: "Browser announcement contents",
      audience: { kind: "COMPANY", targetIds: [] },
      acknowledgementRequired: false,
      reason: "Create a scheduled job for browser validation",
    },
  });
  expect(draft.status()).toBe(200);
  const queued = await context.request.post(`${announcementPath}/publish`, {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: {
      expectedVersion: 0,
      scheduledFor: new Date(Date.now() + 600_000).toISOString(),
      reason: "Scheduled browser fixture",
    },
  });
  expect(queued.status()).toBe(200);
  const announcement = await context.request.get(announcementPath);
  expect(announcement.status()).toBe(200);
  const { publicationJobId } = (await announcement.json()) as { publicationJobId: string };
  expect(publicationJobId).toMatch(/^[0-9a-f-]{36}$/u);
  await page.getByRole("link", { name: "Job", exact: true }).click();
  const jobTable = page.getByRole("table");
  await expect(jobTable).toContainText("Publikasi pengumuman");
  await jobTable
    .getByRole("button", { name: `Lihat detail: ${publicationJobId}`, exact: true })
    .click();
  const jobPanel = page.getByRole("dialog", { name: "Detail job", exact: true });
  await expect(jobPanel).toContainText(publicationJobId);
  await expect(jobPanel).not.toContainText("Browser announcement contents");
  await jobPanel.getByRole("button", { name: "Minta pembatalan", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Batalkan job ini?", exact: true })
    .getByRole("button", { name: "Minta pembatalan", exact: true })
    .click();
  await expect(jobPanel.getByRole("status")).toHaveText("Pembatalan diminta");
  await expect(jobPanel.getByRole("region", { name: "Detail job", exact: true })).toContainText(
    "Dalam antrean",
  );
  const cancelled = await context.request.get(
    `/api/v1/companies/${company}/jobs/${publicationJobId}`,
  );
  expect(cancelled.status()).toBe(200);
  expect(await cancelled.json()).toMatchObject({
    cancellationRequested: true,
    status: "QUEUED",
    version: 1,
  });
  await jobPanel.getByRole("button", { name: "Tutup", exact: true }).click();
  await expect(jobTable).toContainText("Pembatalan diminta");
});
