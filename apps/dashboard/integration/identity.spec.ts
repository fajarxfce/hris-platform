import { createHmac, randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";

function authenticatorCode(secret: string): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  const bits = [...secret]
    .map((letter) => alphabet.indexOf(letter).toString(2).padStart(5, "0"))
    .join("");
  const key = Buffer.from(bits.match(/.{8}/gu)?.map((byte) => Number.parseInt(byte, 2)) ?? []);
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30_000)));
  const digest = createHmac("sha1", key).update(counter).digest();
  const offset = (digest.at(-1) ?? 0) & 15;
  return ((digest.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).toString().padStart(6, "0");
}

test("real API sessions, MFA, people, reports, audit, policy, job cancellation and logout", async ({
  page,
  context,
}) => {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("browser-admin@example.invalid");
  await page.getByLabel("Password", { exact: true }).fill("Incorrect fixture password");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("The email or password is incorrect.");
  await page.getByLabel("Password", { exact: true }).fill("Browser-fixture-password-123!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Account verification" })).toBeVisible();
  await page.getByRole("button", { name: "Set up an authenticator" }).click();
  const key = page.locator(".app-enrollment-key code");
  await expect(key).toBeVisible();
  await page
    .getByLabel("Authenticator code", { exact: true })
    .fill(authenticatorCode((await key.textContent()) ?? ""));
  await page.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Recovery codes" })).toBeVisible();
  await expect(page.locator(".app-recovery-codes li")).toHaveCount(10);
  await page.getByRole("button", { name: "I have saved the codes" }).click();
  await expect(page.getByRole("heading", { name: "No company access" })).toBeVisible();

  const companies: string[] = [];
  for (const index of [0, 1]) {
    const csrf = await context.request.get("/api/v1/auth/csrf");
    const token = (await csrf.json()) as { headerName: string; token: string };
    const created = await context.request.post("/api/v1/companies", {
      headers: { [token.headerName]: token.token, "Idempotency-Key": randomUUID() },
      data: {
        code: `BROWSER${index}`,
        name: index === 0 ? "Browser North" : "Browser South",
        timezone: "Asia/Jakarta",
      },
    });
    expect(created.status()).toBe(200);
    const company = (await created.json()) as { id: string };
    companies.push(company.id);
  }
  await page.reload();
  await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible();
  await page
    .getByRole("combobox", { name: "Company", exact: true })
    .selectOption(companies[1] ?? "");
  await expect(page.getByRole("main")).toContainText("Browser South");
  await expect(page.getByRole("main")).not.toContainText("Browser North");
  await page.getByRole("combobox", { name: "Language" }).selectOption("id");
  await expect(page.getByRole("heading", { name: "Ringkasan" })).toBeVisible();
  const csrf = (await (await context.request.get("/api/v1/auth/csrf")).json()) as {
    headerName: string;
    token: string;
  };
  const employeeId = randomUUID();
  const created = await context.request.post(`/api/v1/companies/${companies[1]}/employees`, {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: {
      id: employeeId,
      employeeNumber: "BROWSER-REPORT",
      person: { id: randomUUID(), legalName: "Report fixture employee", nationality: "ID" },
      terms: {
        effectiveFrom: "2026-01-01",
        startDate: "2026-01-01",
        status: "ACTIVE",
        contract: "PERMANENT",
      },
      reason: "Browser report fixture",
    },
  });
  expect(created.status()).toBe(200);
  await page.getByRole("link", { name: "Karyawan", exact: true }).click();
  await page.getByLabel("Tanggal efektif", { exact: true }).fill("2026-10-01");
  await page.getByRole("button", { name: "Terapkan", exact: true }).click();
  const directory = page.getByRole("table", { name: "Direktori karyawan", exact: true });
  await expect(directory.getByRole("row")).toHaveCount(2);
  await directory
    .getByRole("button", {
      name: "Lihat detail: Report fixture employee (BROWSER-REPORT)",
      exact: true,
    })
    .click();
  await expect(
    page.getByRole("heading", { name: "Report fixture employee", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("region", { name: "Employment", exact: true })).toContainText(
    "BROWSER-REPORT",
  );
  await page.getByRole("tab", { name: "Riwayat employment", exact: true }).click();
  const employmentHistory = page.getByRole("table", { name: "Riwayat employment", exact: true });
  await expect(employmentHistory.getByRole("row")).toHaveCount(2);
  await employmentHistory.getByRole("button", { name: "Lihat detail: 0", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Detail revisi", exact: true })).toContainText(
    "Browser report fixture",
  );
  await page.getByRole("button", { name: "Tutup", exact: true }).click();
  await page.getByRole("link", { name: "Laporan", exact: true }).click();
  await page.getByLabel("Tanggal laporan", { exact: true }).fill("2026-10-01");
  await page.getByRole("button", { name: "Terapkan", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Headcount" })).toBeVisible();
  await expect(page.getByRole("region", { name: "Hubungan kerja", exact: true })).toContainText(
    "1",
  );
  await expect(page.getByRole("region", { name: "Jumlah orang", exact: true })).toContainText("1");
  await expect(page.getByRole("region", { name: "Status kerja", exact: true })).toContainText(
    "Aktif",
  );
  await page
    .getByRole("group", { name: "Filter laporan" })
    .getByRole("combobox", { name: "Perusahaan", exact: true })
    .click();
  await page.getByRole("menuitemcheckbox", { name: /Browser North/u }).click();
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Terapkan", exact: true }).click();
  const breakdown = page.getByRole("table", { name: "Per perusahaan" });
  await expect(breakdown.getByRole("row")).toHaveCount(3);
  await expect(breakdown.getByRole("row").filter({ hasText: "Browser North" })).toContainText("0");
  await expect(breakdown.getByRole("row").filter({ hasText: "Browser South" })).toContainText("1");
  await expect(page.getByRole("region", { name: "Jumlah orang", exact: true })).toContainText("1");
  await page.getByRole("link", { name: "Log audit", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Log audit", exact: true })).toBeVisible();
  await page.getByLabel("ID resource", { exact: true }).fill(employeeId);
  await page.getByRole("button", { name: "Terapkan", exact: true }).click();
  const audit = page.getByRole("table", { name: "Event", exact: true });
  await expect(audit.getByRole("row")).toHaveCount(2);
  await audit.getByRole("button", { name: /^Lihat detail:/u }).click();
  const details = page.getByRole("dialog", { name: "Event audit", exact: true });
  await expect(details).toContainText(employeeId);
  await expect(details).toContainText(companies[1] ?? "");
  await expect(details).not.toContainText("Report fixture employee");
  await expect(details).not.toContainText("Browser report fixture");
  await page.getByRole("button", { name: "Tutup", exact: true }).click();
  await page.getByRole("link", { name: "Policy client", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText(
    "Belum ada konfigurasi perusahaan yang disimpan.",
  );
  const policyPath = `/api/v1/companies/${companies[1]}/settings/client-policy`;
  for (const version of [0, 1]) {
    const response = await context.request.put(policyPath, {
      headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
      data: {
        expectedVersion: version === 0 ? null : 0,
        activateAt: version === 0 ? null : new Date(Date.now() + 600_000).toISOString(),
        disabledModules: version === 0 ? ["EXPENSES"] : ["PAYROLL"],
        minimumBuilds: { android: version + 1, ios: 0, web: 0 },
        maintenance: null,
        reason: `Browser policy revision ${version}`,
      },
    });
    expect(response.status()).toBe(200);
  }
  await page.getByRole("button", { name: "Muat ulang", exact: true }).click();
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
  const announcementPath = `/api/v1/companies/${companies[1]}/announcements/${randomUUID()}`;
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
    `/api/v1/companies/${companies[1]}/jobs/${publicationJobId}`,
  );
  expect(cancelled.status()).toBe(200);
  expect(await cancelled.json()).toMatchObject({
    cancellationRequested: true,
    status: "QUEUED",
    version: 1,
  });
  await jobPanel.getByRole("button", { name: "Tutup", exact: true }).click();
  await expect(jobTable).toContainText("Pembatalan diminta");
  const signedOut = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === "/api/v1/auth/logout" &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Keluar", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Masuk", exact: true })).toBeVisible();
  expect((await signedOut).status()).toBe(204);
  expect((await context.request.get("/api/v1/me")).status()).toBe(401);
  await expect(page.getByLabel("Password", { exact: true })).toHaveValue("");
});
