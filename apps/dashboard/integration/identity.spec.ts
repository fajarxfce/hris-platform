import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import type { EmployeeImportStartDto } from "../src/features/people/data/models/employee-import-start-dto";
import { authenticatorCode } from "./fixtures/authenticator";

test("real API sessions, MFA, organization, people, lifecycle, reports, audit, policy, jobs and logout", async ({
  page,
  context,
}) => {
  // This cumulative workflow exceeded 90 seconds on CI; individual assertions remain bounded.
  test.setTimeout(120_000);
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
  const recoveryCodes = await page.locator(".app-recovery-codes li code").allTextContents();
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
  let csrf = (await (await context.request.get("/api/v1/auth/csrf")).json()) as {
    headerName: string;
    token: string;
  };
  const branchId = randomUUID();
  const departmentId = randomUUID();
  for (const { id: unitId, ...unit } of [
    {
      id: branchId,
      code: "HQ",
      name: "Browser office",
      kind: "BRANCH",
      parentId: null,
      timezone: "Asia/Jakarta",
    },
    {
      id: departmentId,
      code: "RND",
      name: "R&D_100%",
      kind: "DEPARTMENT",
      parentId: branchId,
      timezone: null,
    },
  ]) {
    const saved = await context.request.put(
      `/api/v1/companies/${companies[1]}/organization-units/${unitId}`,
      {
        headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
        data: { ...unit, active: true, expectedVersion: null },
      },
    );
    expect(saved.status()).toBe(200);
  }
  await page.getByRole("link", { name: "Organisasi", exact: true }).click();
  await page.getByLabel("Nama atau kode", { exact: true }).fill("r&d_100%");
  await page.getByRole("combobox", { name: "Jenis", exact: true }).selectOption("DEPARTMENT");
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("true");
  await page.getByRole("button", { name: "Terapkan", exact: true }).click();
  const units = page.getByRole("table", { name: "Unit organisasi", exact: true });
  await expect(units.getByRole("row")).toHaveCount(2);
  await units.getByRole("button", { name: "Lihat detail: R&D_100% (RND)", exact: true }).click();
  await expect(page.getByRole("region", { name: "Unit induk", exact: true })).toContainText(
    "Browser office",
  );
  await page.getByRole("link", { name: "Buka unit induk", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Browser office", exact: true })).toBeVisible();
  await expect(page.getByRole("status")).toHaveText("Unit ini tidak memiliki induk.");
  await page.getByRole("link", { name: "Kembali ke organisasi", exact: true }).click();
  await expect(page.getByLabel("Nama atau kode", { exact: true })).toHaveValue("r&d_100%");
  const unsubmittedFilter = page.getByLabel("Nama atau kode", { exact: true });
  await unsubmittedFilter.fill("Unsubmitted organization filter");
  await unsubmittedFilter.evaluate((input) =>
    input.setAttribute("data-retention-probe", "retained"),
  );
  await page
    .getByRole("banner")
    .getByRole("button", { name: "Verifikasi akun", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Verifikasi akun", exact: true });
  await verification.getByRole("button", { name: "Gunakan recovery code", exact: true }).click();
  await verification.getByLabel("Recovery code", { exact: true }).fill(recoveryCodes[0] ?? "");
  await verification.getByRole("button", { name: "Verifikasi", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(unsubmittedFilter).toHaveValue("Unsubmitted organization filter");
  await expect(unsubmittedFilter).toHaveAttribute("data-retention-probe", "retained");
  const previousCsrf = csrf.token;
  csrf = await (await context.request.get("/api/v1/auth/csrf")).json();
  expect(csrf.token).not.toBe(previousCsrf);
  await page.getByRole("link", { name: "Buat unit", exact: true }).click();
  await page.getByLabel("Kode", { exact: true }).fill("SUPPORT");
  await page.getByLabel("Nama", { exact: true }).fill("Browser support");
  await page.getByRole("button", { name: "Pilih unit induk", exact: true }).click();
  const parentPicker = page.getByRole("dialog", { name: "Pilih unit induk", exact: true });
  await parentPicker
    .getByRole("button", { name: "Pilih: Browser office (HQ)", exact: true })
    .click();
  let committedUnit: { id: string; version: number } | null = null;
  await page.route(
    "**/api/v1/companies/*/organization-units/*",
    async (route) => {
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      committedUnit = await response.json();
      await route.abort("failed");
    },
    { times: 1 },
  );
  await page.getByRole("button", { name: "Simpan", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Hasil penyimpanan belum dipastikan");
  await page.getByRole("button", { name: "Coba simpan kembali", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Unit tersimpan.");
  await page.getByRole("link", { name: "Lihat detail", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Browser support", exact: true })).toBeVisible();
  expect(committedUnit).toMatchObject({ version: 0 });
  await page.getByRole("link", { name: "Edit unit", exact: true }).click();
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("false");
  await page.getByRole("button", { name: "Simpan", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Unit tersimpan.");
  await page.getByRole("link", { name: "Lihat detail", exact: true }).click();
  await expect(page.getByRole("region", { name: "Ringkasan", exact: true })).toContainText(
    "Nonaktif",
  );
  await expect(
    page
      .getByRole("region", { name: "Ringkasan", exact: true })
      .locator(".app-property-row")
      .filter({ hasText: "Versi" }),
  ).toContainText("1");
  await page.getByRole("link", { name: "Karyawan", exact: true }).click();
  await page.getByRole("link", { name: "Buat karyawan", exact: true }).click();
  await page.getByLabel("Nama lengkap", { exact: true }).fill("Report fixture employee");
  await page.getByLabel("Kewarganegaraan", { exact: true }).fill("ID");
  await page.getByLabel("Nomor karyawan", { exact: true }).fill("BROWSER-REPORT");
  await page.getByLabel("Tanggal mulai", { exact: true }).fill("2026-01-01");
  await page.getByLabel("Alasan", { exact: true }).fill("Browser onboarding fixture");
  await page.getByRole("button", { name: "Pilih Cabang", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Pilih Cabang", exact: true })
    .getByRole("button", { name: "Pilih: HQ · Browser office", exact: true })
    .click();
  await page.getByRole("button", { name: "Pilih Departemen", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Pilih Departemen", exact: true })
    .getByRole("button", { name: "Pilih: RND · R&D_100%", exact: true })
    .click();
  let employeeId = "";
  let creationAttempts = 0;
  await page.route("**/api/v1/companies/*/employees", async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    creationAttempts += 1;
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    const receipt = await response.json();
    expect(receipt.version).toBe(0);
    if (creationAttempts === 1) {
      employeeId = receipt.id;
      await route.abort("failed");
    } else {
      expect(receipt.id).toBe(employeeId);
      await route.fulfill({ response });
    }
  });
  await page.getByRole("button", { name: "Buat karyawan", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Hasil pembuatan belum terkonfirmasi");
  await page.getByRole("button", { name: "Coba pembuatan kembali", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Karyawan dibuat.");
  expect(creationAttempts).toBe(2);
  const employeeSnapshot = await (
    await context.request.get(
      `/api/v1/companies/${companies[1]}/employees/${employeeId}?asOf=2026-10-01`,
    )
  ).json();
  expect(employeeSnapshot).toMatchObject({
    version: 0,
    appliedRevision: 0,
    terms: { branchId, departmentId, managerId: null },
  });
  await page.getByRole("link", { name: "Kembali", exact: true }).click();
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
  await page.getByRole("link", { name: "Profil pribadi", exact: true }).click();
  await expect(page.getByRole("region", { name: "Data pribadi", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Edit profil", exact: true }).click();
  await page.getByLabel("Tanggal lahir", { exact: true }).fill("1995-06-07");
  await page.getByLabel("Email", { exact: true }).fill("profile@internal");
  await page
    .getByLabel("Alasan perubahan", { exact: true })
    .fill("Verified browser profile correction");
  await page.getByRole("button", { name: "Simpan", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Profil tersimpan.");
  await page.getByRole("link", { name: "Lihat profil", exact: true }).click();
  const profileResponse = await context.request.get(
    `/api/v1/companies/${companies[1]}/employees/${employeeId}/profile`,
  );
  expect(profileResponse.status()).toBe(200);
  const profileSnapshot = await profileResponse.json();
  expect(profileSnapshot).toMatchObject({
    version: 1,
    birthDate: "1995-06-07",
    email: "profile@internal",
  });
  expect(profileSnapshot.personId).not.toBe(employeeId);
  const employmentResponse = await context.request.get(
    `/api/v1/companies/${companies[1]}/employees/${employeeId}?asOf=2026-10-01`,
  );
  expect(employmentResponse.status()).toBe(200);
  expect(await employmentResponse.json()).toMatchObject({ version: 0, appliedRevision: 0 });
  await page.getByRole("tab", { name: "Riwayat profil", exact: true }).click();
  const profileHistory = page.getByRole("table", { name: "Riwayat profil", exact: true });
  await expect(profileHistory.getByRole("row")).toHaveCount(3);
  await profileHistory.getByRole("button", { name: "Lihat revisi: 1", exact: true }).click();
  await expect(
    page.getByRole("dialog", { name: "Detail revisi profil", exact: true }),
  ).toContainText("Verified browser profile correction");
  await page.getByRole("button", { name: "Tutup", exact: true }).click();
  await page.getByRole("link", { name: "Kembali", exact: true }).click();
  await expect(page.getByRole("region", { name: "Employment", exact: true })).toContainText(
    "BROWSER-REPORT",
  );
  await page.getByRole("tab", { name: "Riwayat employment", exact: true }).click();
  const employmentHistory = page.getByRole("table", { name: "Riwayat employment", exact: true });
  await expect(employmentHistory.getByRole("row")).toHaveCount(2);
  await employmentHistory.getByRole("button", { name: "Lihat detail: 0", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Detail revisi", exact: true })).toContainText(
    "Browser onboarding fixture",
  );
  await page.getByRole("button", { name: "Tutup", exact: true }).click();
  await page.getByRole("link", { name: "Edit employment", exact: true }).click();
  await expect(page.getByLabel("Tanggal mulai", { exact: true })).toHaveValue("2026-01-01");
  await expect(page.getByText("HQ · Browser office", { exact: true })).toBeVisible();
  await expect(page.getByText("RND · R&D_100%", { exact: true })).toBeVisible();
  const employmentEffectiveDate = `${new Date().getUTCFullYear() + 1}-01-01`;
  await page.getByLabel("Berlaku sejak", { exact: true }).fill(employmentEffectiveDate);
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("SUSPENDED");
  await page.getByLabel("Alasan", { exact: true }).fill("Approved browser employment change");
  const revisionAttempts: { operation: string | undefined; payload: string | null }[] = [];
  await page.route("**/api/v1/companies/*/employees/*/revisions", async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    revisionAttempts.push({
      operation: route.request().headers()["idempotency-key"],
      payload: route.request().postData(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual({ id: employeeId, version: 1 });
    if (revisionAttempts.length === 1) await route.abort("failed");
    else await route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Simpan revisi", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Hasil revisi belum terkonfirmasi");
  await page.getByRole("button", { name: "Coba revisi kembali", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Revisi employment tersimpan.");
  expect(revisionAttempts).toHaveLength(2);
  expect(revisionAttempts[1]).toEqual(revisionAttempts[0]);
  const historicalEmployment = await context.request.get(
    `/api/v1/companies/${companies[1]}/employees/${employeeId}/employment?asOf=2026-10-01`,
  );
  expect(historicalEmployment.status()).toBe(200);
  expect(await historicalEmployment.json()).toMatchObject({
    employee: { version: 1, appliedRevision: 0, terms: { status: "ACTIVE" } },
  });
  await page.getByRole("link", { name: "Lihat detail", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`asOf=${employmentEffectiveDate}`, "u"));
  await expect(page.getByRole("region", { name: "Employment", exact: true })).toContainText(
    "Ditangguhkan",
  );
  await page.getByRole("tab", { name: "Riwayat employment", exact: true }).click();
  await expect(employmentHistory.getByRole("row")).toHaveCount(3);
  await employmentHistory.getByRole("button", { name: "Lihat detail: 1", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Detail revisi", exact: true })).toContainText(
    "Approved browser employment change",
  );
  await page.getByRole("button", { name: "Tutup", exact: true }).click();
  await employmentHistory.getByRole("button", { name: "Lihat detail: 1", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Detail revisi", exact: true })
    .getByRole("link", { name: "Tinjau pembatalan", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Revisi", exact: true })).toContainText(
    "Approved browser employment change",
  );
  await page.getByLabel("Alasan pembatalan", { exact: true }).fill("Cancelled browser schedule");
  const cancellationAttempts: { operation: string | undefined; payload: string | null }[] = [];
  await page.route("**/api/v1/companies/*/employees/*/revisions/*/cancel", async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    cancellationAttempts.push({
      operation: route.request().headers()["idempotency-key"],
      payload: route.request().postData(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual({ id: employeeId, version: 2 });
    if (cancellationAttempts.length === 1) await route.abort("failed");
    else await route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Batalkan revisi", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Hasil pembatalan belum terkonfirmasi");
  await page.getByRole("button", { name: "Coba pembatalan kembali", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Revisi employment dibatalkan.");
  expect(cancellationAttempts).toHaveLength(2);
  expect(cancellationAttempts[1]).toEqual(cancellationAttempts[0]);
  const effectiveAfterCancellation = await context.request.get(
    `/api/v1/companies/${companies[1]}/employees/${employeeId}?asOf=${employmentEffectiveDate}`,
  );
  expect(effectiveAfterCancellation.status()).toBe(200);
  expect(await effectiveAfterCancellation.json()).toMatchObject({
    version: 2,
    appliedRevision: 0,
    terms: { status: "ACTIVE" },
  });
  await page.getByRole("link", { name: "Lihat riwayat", exact: true }).click();
  await expect(employmentHistory.getByRole("row")).toHaveCount(3);
  await employmentHistory.getByRole("button", { name: "Lihat detail: 1", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Detail revisi", exact: true })).toContainText(
    "Cancelled browser schedule",
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
  await expect(audit.getByRole("row")).toHaveCount(4);
  await audit
    .getByRole("row")
    .filter({ hasText: "people.revision_cancelled" })
    .getByRole("button", { name: /^Lihat detail:/u })
    .click();
  const details = page.getByRole("dialog", { name: "Event audit", exact: true });
  await expect(details).toContainText(employeeId);
  await expect(details).toContainText(companies[1] ?? "");
  await expect(details).not.toContainText("Report fixture employee");
  await expect(details).not.toContainText("Browser report fixture");
  await page.getByRole("button", { name: "Tutup", exact: true }).click();
  await page.getByRole("link", { name: "Template lifecycle", exact: true }).click();
  await page.getByRole("link", { name: "Buat template", exact: true }).click();
  await page.getByLabel("Kode", { exact: true }).fill("BROWSER_ONBOARD");
  await page.getByLabel("Nama", { exact: true }).fill("Browser checklist");
  const firstTask = page.getByRole("group", { name: "Tugas 1", exact: true });
  await firstTask.getByLabel("Key", { exact: true }).fill("equipment");
  await firstTask.getByLabel("Judul tugas", { exact: true }).fill("Review equipment");
  await firstTask.getByLabel("Offset jatuh tempo (hari)", { exact: true }).fill("-2");
  await page.getByLabel("Alasan", { exact: true }).fill("Browser lifecycle template");
  await page.getByRole("button", { name: "Simpan template", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Template tersimpan.");
  await page.getByRole("link", { name: "Lihat template", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Browser checklist", exact: true })).toBeVisible();
  const templateId = new URL(page.url()).pathname.split("/").at(-1);
  expect(templateId).toMatch(/^[0-9a-f-]{36}$/u);
  const templatePath = `/api/v1/companies/${companies[1]}/lifecycle/templates/${templateId}`;
  const templateBefore = await context.request.get(templatePath);
  expect(templateBefore.status()).toBe(200);
  expect(await templateBefore.json()).toMatchObject({ version: 0, code: "BROWSER_ONBOARD" });
  const transitionId = randomUUID();
  const lifecycleActor = await (await context.request.get("/api/v1/me")).json();
  csrf = await (await context.request.get("/api/v1/auth/csrf")).json();
  const transition = await context.request.post(
    `/api/v1/companies/${companies[1]}/lifecycle/cases`,
    {
      headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
      data: {
        id: transitionId,
        employmentId: employeeId,
        templateId,
        templateVersion: 0,
        targetDate: new Date().toISOString().slice(0, 10),
        assignees: { equipment: lifecycleActor.account.id },
        reason: "Browser onboarding",
      },
    },
  );
  expect(transition.status()).toBe(200);
  await page.getByRole("link", { name: "Edit template", exact: true }).click();
  await page.getByLabel("Nama", { exact: true }).fill("Browser revised checklist");
  await page.getByRole("button", { name: "Tambah tugas", exact: true }).click();
  const addedTask = page.getByRole("group", { name: "Tugas 2", exact: true });
  await addedTask.getByLabel("Key", { exact: true }).fill("welcome");
  await addedTask.getByLabel("Judul tugas", { exact: true }).fill("Welcome session");
  await addedTask.getByRole("checkbox", { name: "Wajib", exact: true }).uncheck();
  await page.getByLabel("Alasan", { exact: true }).fill("Revise browser checklist");
  const templateWrites: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**${templatePath}`, async (route) => {
    if (route.request().method() !== "PUT") return route.fallback();
    templateWrites.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ id: templateId, version: 1 });
    if (templateWrites.length === 1) return route.abort("connectionfailed");
    return route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Simpan template", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Hasil penyimpanan belum terkonfirmasi.");
  await page.getByRole("button", { name: "Ulangi penyimpanan awal", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Template tersimpan.");
  expect(templateWrites).toHaveLength(2);
  expect(templateWrites[1]).toEqual(templateWrites[0]);
  await page.unroute(`**${templatePath}`);
  await page.getByRole("link", { name: "Lihat template", exact: true }).click();
  await expect(page.getByRole("table", { name: "Checklist", exact: true })).toContainText(
    "Welcome session",
  );
  const templateAfter = await context.request.get(templatePath);
  expect(await templateAfter.json()).toMatchObject({
    version: 1,
    name: "Browser revised checklist",
  });
  const retainedTransition = await context.request.get(
    `/api/v1/companies/${companies[1]}/lifecycle/cases/${transitionId}`,
  );
  expect(retainedTransition.status()).toBe(200);
  const retainedChecklist = await retainedTransition.json();
  expect(retainedChecklist).toMatchObject({
    templateVersion: 0,
    templateName: "Browser checklist",
  });
  expect(retainedChecklist.tasks).toHaveLength(1);
  const foreignTemplate = await context.request.get(
    `/api/v1/companies/${companies[0]}/lifecycle/templates/${templateId}`,
  );
  expect(foreignTemplate.status()).toBe(404);
  expect(Object.keys(retainedChecklist.employee).sort()).toEqual(["employeeNumber", "id", "name"]);
  expect(retainedChecklist.employee.id).toBe(employeeId);
  await page.getByRole("link", { name: "Tugas saya", exact: true }).click();
  const assignedTasks = page.getByRole("table", { name: "Tugas saya", exact: true });
  await expect(assignedTasks.getByRole("row")).toHaveCount(2);
  await expect(assignedTasks).toContainText(retainedChecklist.employee.name);
  await assignedTasks
    .getByRole("button", {
      name: `Lihat tugas: Review equipment · ${retainedChecklist.employee.name}`,
      exact: true,
    })
    .click();
  const taskDetails = page.getByRole("dialog", { name: "Review equipment", exact: true });
  await expect(taskDetails).toContainText(retainedChecklist.employee.employeeNumber);
  await expect(taskDetails).toContainText("Anda");
  const taskPath = `/api/v1/companies/${companies[1]}/lifecycle/cases/${transitionId}/tasks/equipment`;
  const taskWrites: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**${taskPath}`, async (route) => {
    if (route.request().method() !== "PUT") return route.fallback();
    taskWrites.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ id: transitionId, version: 1 });
    if (taskWrites.length === 1) return route.abort("connectionfailed");
    return route.fulfill({ response });
  });
  await taskDetails.getByLabel("Alasan", { exact: true }).fill("Browser equipment received");
  await taskDetails.getByRole("button", { name: "Simpan tugas", exact: true }).click();
  await expect(taskDetails.getByRole("status")).toContainText("Penyimpanan belum terkonfirmasi.");
  await taskDetails.getByRole("button", { name: "Coba simpan kembali", exact: true }).click();
  await expect(taskDetails).toHaveCount(0);
  expect(taskWrites).toHaveLength(2);
  expect(taskWrites[1]).toEqual(taskWrites[0]);
  expect(taskWrites[0]?.body).toEqual({
    expectedVersion: 0,
    status: "DONE",
    reason: "Browser equipment received",
  });
  await page.unroute(`**${taskPath}`);
  await expect(page.getByRole("status")).toHaveText("Tidak ada tugas tertunda untuk Anda.");
  await page.getByRole("link", { name: "Proses lifecycle", exact: true }).click();
  const lifecycleCases = page.getByRole("table", { name: "Daftar proses", exact: true });
  await expect(lifecycleCases.getByRole("row")).toHaveCount(2);
  await lifecycleCases
    .getByRole("button", {
      name: `Buka proses: ${retainedChecklist.employee.name} · ${retainedChecklist.employee.employeeNumber}`,
      exact: true,
    })
    .click();
  await expect(
    page.getByRole("table", { name: "Checklist", exact: true }).getByRole("row"),
  ).toHaveCount(2);
  await expect(page.getByRole("main")).toContainText("Browser checklist");
  await expect(page.getByRole("main")).not.toContainText("Browser revised checklist");
  await page.getByRole("tab", { name: "Riwayat", exact: true }).click();
  const lifecycleHistory = page.getByRole("table", { name: "Riwayat", exact: true });
  await expect(lifecycleHistory.getByRole("row")).toHaveCount(3);
  await lifecycleHistory.getByRole("button", { name: "Lihat perubahan: 0", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Detail perubahan", exact: true })).toContainText(
    "Browser onboarding",
  );
  await page.getByRole("button", { name: "Tutup", exact: true }).click();
  await lifecycleHistory.getByRole("button", { name: "Lihat perubahan: 1", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Detail perubahan", exact: true })).toContainText(
    "Browser equipment received",
  );
  await page.getByRole("button", { name: "Tutup", exact: true }).click();
  await page.getByRole("tab", { name: "Ringkasan", exact: true }).click();
  await page.getByRole("button", { name: "Lihat tugas: Review equipment", exact: true }).click();
  await taskDetails.getByLabel("Status baru", { exact: true }).selectOption("PENDING");
  await taskDetails.getByLabel("Alasan", { exact: true }).fill("Browser equipment recheck");
  await taskDetails.getByRole("button", { name: "Simpan tugas", exact: true }).click();
  await expect(taskDetails).toHaveCount(0);
  const taskCasePath = `/api/v1/companies/${companies[1]}/lifecycle/cases/${transitionId}`;
  const reopenedCase = await (await context.request.get(taskCasePath)).json();
  expect(reopenedCase).toMatchObject({
    version: 2,
    tasks: [{ key: "equipment", status: "PENDING", completedBy: null, completedAt: null }],
  });
  const taskHistory = await (await context.request.get(`${taskCasePath}/history?after=0`)).json();
  expect(
    taskHistory.items.map((event: { version: number; action: string }) => [
      event.version,
      event.action,
    ]),
  ).toEqual([
    [1, "TASK_DONE"],
    [2, "TASK_PENDING"],
  ]);
  await page.getByRole("button", { name: "Lihat tugas: Review equipment", exact: true }).click();
  await taskDetails.getByRole("button", { name: "Tetapkan tugas", exact: true }).click();
  const assignment = page.getByRole("dialog", {
    name: "Tetapkan tugas: Review equipment",
    exact: true,
  });
  await assignment.getByRole("button", { name: "Hapus penanggung jawab", exact: true }).click();
  await assignment.getByLabel("Alasan", { exact: true }).fill("Browser remove assignee");
  await assignment.getByRole("button", { name: "Simpan penugasan", exact: true }).click();
  await expect(assignment).toHaveCount(0);
  const unassignedCase = await (await context.request.get(taskCasePath)).json();
  expect(unassignedCase).toMatchObject({
    version: 3,
    tasks: [{ key: "equipment", status: "PENDING", assigneeId: null }],
  });
  await page.getByRole("button", { name: "Lihat tugas: Review equipment", exact: true }).click();
  await taskDetails.getByRole("button", { name: "Tetapkan tugas", exact: true }).click();
  await assignment.getByRole("button", { name: "Pilih anggota", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Pilih anggota", exact: true })
    .getByRole("button", {
      name: `Pilih: ${lifecycleActor.account.displayName} · ${lifecycleActor.account.id}`,
      exact: true,
    })
    .click();
  await assignment.getByLabel("Alasan", { exact: true }).fill("Browser reassign equipment");
  const assignmentWrites: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**${taskPath}/assignee`, async (route) => {
    if (route.request().method() !== "PUT") return route.fallback();
    assignmentWrites.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ id: transitionId, version: 4 });
    if (assignmentWrites.length === 1) return route.abort("connectionfailed");
    return route.fulfill({ response });
  });
  await assignment.getByRole("button", { name: "Simpan penugasan", exact: true }).click();
  await expect(assignment.getByRole("status")).toContainText("Penyimpanan belum terkonfirmasi.");
  await assignment.getByRole("button", { name: "Coba simpan kembali", exact: true }).click();
  await expect(assignment).toHaveCount(0);
  expect(assignmentWrites).toHaveLength(2);
  expect(assignmentWrites[1]).toEqual(assignmentWrites[0]);
  expect(assignmentWrites[0]?.body).toEqual({
    expectedVersion: 3,
    assigneeId: lifecycleActor.account.id,
    reason: "Browser reassign equipment",
  });
  await page.unroute(`**${taskPath}/assignee`);
  const assignedCase = await (await context.request.get(taskCasePath)).json();
  expect(assignedCase).toMatchObject({
    version: 4,
    tasks: [{ key: "equipment", status: "PENDING", assigneeId: lifecycleActor.account.id }],
  });
  const assignmentHistory = await (
    await context.request.get(`${taskCasePath}/history?after=2`)
  ).json();
  expect(
    assignmentHistory.items.map(
      (event: { version: number; action: string; assigneeId: string | null }) => [
        event.version,
        event.action,
        event.assigneeId,
      ],
    ),
  ).toEqual([
    [3, "ASSIGNED", null],
    [4, "ASSIGNED", lifecycleActor.account.id],
  ]);
  expect(
    (
      await context.request.get(`/api/v1/companies/${companies[0]}/lifecycle/cases/${transitionId}`)
    ).status(),
  ).toBe(404);
  const offboardingTemplateId = randomUUID();
  csrf = await (await context.request.get("/api/v1/auth/csrf")).json();
  const offboardingTemplate = await context.request.put(
    `/api/v1/companies/${companies[1]}/lifecycle/templates/${offboardingTemplateId}`,
    {
      headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
      data: {
        expectedVersion: null,
        code: "BROWSER_OFFBOARD",
        name: "Browser offboarding",
        kind: "OFFBOARDING",
        active: true,
        tasks: [
          { key: "equipment_return", title: "Return equipment", required: true, dueDays: 0 },
          { key: "exit_meeting", title: "Exit meeting", required: false, dueDays: -1 },
        ],
        reason: "Browser offboarding fixture",
      },
    },
  );
  expect(offboardingTemplate.status()).toBe(200);
  await page.getByRole("link", { name: "Karyawan", exact: true }).click();
  await page.getByLabel("Tanggal efektif", { exact: true }).fill("2026-10-01");
  await page
    .getByLabel("Nama atau nomor karyawan", { exact: true })
    .fill(retainedChecklist.employee.employeeNumber);
  await page.getByRole("button", { name: "Terapkan", exact: true }).click();
  await page
    .getByRole("button", {
      name: `Lihat detail: ${retainedChecklist.employee.name} (${retainedChecklist.employee.employeeNumber})`,
      exact: true,
    })
    .click();
  await page.getByRole("link", { name: "Mulai proses lifecycle", exact: true }).click();
  await page.getByRole("button", { name: "Pilih template", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Pilih template", exact: true })
    .getByRole("button", { name: "Pilih: Browser offboarding (BROWSER_OFFBOARD)", exact: true })
    .click();
  await page.getByLabel("Tanggal target", { exact: true }).fill("2026-10-03");
  await expect(page.getByRole("table", { name: "Checklist", exact: true })).toContainText(
    "2 Okt 2026",
  );
  await page.getByLabel("Alasan", { exact: true }).fill("Browser departure checklist");
  const caseCreationPath = `/api/v1/companies/${companies[1]}/lifecycle/cases`;
  const caseCreations: { operation: string | undefined; body: unknown }[] = [];
  let newCaseId = "";
  await page.route(`**${caseCreationPath}`, async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    const body = route.request().postDataJSON();
    caseCreations.push({ operation: route.request().headers()["idempotency-key"], body });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    const receipt = await response.json();
    expect(receipt).toMatchObject({ id: body.id, version: 0 });
    newCaseId = receipt.id;
    if (caseCreations.length === 1) return route.abort("connectionfailed");
    return route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Mulai proses", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Hasil belum terkonfirmasi.");
  await page.getByRole("button", { name: "Ulangi request awal", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Proses lifecycle dimulai.");
  expect(caseCreations).toHaveLength(2);
  expect(caseCreations[1]).toEqual(caseCreations[0]);
  expect(caseCreations[0]?.body).toEqual({
    id: newCaseId,
    employmentId: employeeId,
    templateId: offboardingTemplateId,
    templateVersion: 0,
    targetDate: "2026-10-03",
    assignees: {},
    reason: "Browser departure checklist",
  });
  await page.unroute(`**${caseCreationPath}`);
  await page.getByRole("link", { name: "Buka proses", exact: true }).click();
  await expect(page.getByRole("region", { name: "Ringkasan", exact: true })).toContainText(
    "Browser offboarding",
  );
  const createdCase = await (await context.request.get(`${caseCreationPath}/${newCaseId}`)).json();
  expect(createdCase).toMatchObject({
    id: newCaseId,
    employmentId: employeeId,
    kind: "OFFBOARDING",
    version: 0,
    status: "OPEN",
    templateVersion: 0,
  });
  expect(
    createdCase.tasks.map(
      (task: { key: string; dueDate: string; assigneeId: string | null; status: string }) => [
        task.key,
        task.dueDate,
        task.assigneeId,
        task.status,
      ],
    ),
  ).toEqual([
    ["equipment_return", "2026-10-03", null, "PENDING"],
    ["exit_meeting", "2026-10-02", null, "PENDING"],
  ]);
  const createdHistory = await (
    await context.request.get(`${caseCreationPath}/${newCaseId}/history`)
  ).json();
  expect(createdHistory.items).toHaveLength(1);
  expect(createdHistory.items[0]).toMatchObject({
    version: 0,
    action: "CREATED",
    reason: "Browser departure checklist",
  });
  await page.getByRole("button", { name: "Batalkan proses", exact: true }).click();
  const cancellation = page.getByRole("dialog", { name: "Batalkan proses", exact: true });
  await cancellation.getByLabel("Alasan", { exact: true }).fill("Browser departure cancelled");
  const cancelPath = `${caseCreationPath}/${newCaseId}/cancel`;
  const cancellationWrites: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**${cancelPath}`, async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    cancellationWrites.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ id: newCaseId, version: 1 });
    if (cancellationWrites.length === 1) return route.abort("connectionfailed");
    return route.fulfill({ response });
  });
  await cancellation.getByRole("button", { name: "Batalkan proses", exact: true }).click();
  await expect(cancellation.getByRole("status")).toContainText("Hasil belum terkonfirmasi.");
  await cancellation.getByRole("button", { name: "Ulangi tindakan awal", exact: true }).click();
  await expect(cancellation).toHaveCount(0);
  expect(cancellationWrites).toHaveLength(2);
  expect(cancellationWrites[1]).toEqual(cancellationWrites[0]);
  expect(cancellationWrites[0]?.body).toEqual({
    expectedVersion: 0,
    reason: "Browser departure cancelled",
  });
  await page.unroute(`**${cancelPath}`);
  const cancelledCase = await (
    await context.request.get(`${caseCreationPath}/${newCaseId}`)
  ).json();
  expect(cancelledCase).toMatchObject({ id: newCaseId, version: 1, status: "CANCELLED" });
  expect(cancelledCase.tasks).toEqual(createdCase.tasks);
  const cancelledHistory = await (
    await context.request.get(`${caseCreationPath}/${newCaseId}/history?after=0`)
  ).json();
  expect(cancelledHistory.items).toHaveLength(1);
  expect(cancelledHistory.items[0]).toMatchObject({
    version: 1,
    action: "CANCELLED",
    reason: "Browser departure cancelled",
  });
  await page.getByRole("link", { name: "Proses lifecycle", exact: true }).click();
  await expect(lifecycleCases.getByRole("row")).toHaveCount(2);
  await lifecycleCases
    .getByRole("button", {
      name: `Buka proses: ${retainedChecklist.employee.name} · ${retainedChecklist.employee.employeeNumber}`,
      exact: true,
    })
    .click();
  await expect(
    page.getByRole("button", { name: "Selesaikan onboarding", exact: true }),
  ).toBeDisabled();
  await page.getByRole("button", { name: "Lihat tugas: Review equipment", exact: true }).click();
  await taskDetails.getByLabel("Alasan", { exact: true }).fill("Browser equipment verified");
  await taskDetails.getByRole("button", { name: "Simpan tugas", exact: true }).click();
  await expect(taskDetails).toHaveCount(0);
  await page.getByRole("button", { name: "Selesaikan onboarding", exact: true }).click();
  const completion = page.getByRole("dialog", { name: "Selesaikan onboarding", exact: true });
  await completion.getByLabel("Alasan", { exact: true }).fill("Browser onboarding completed");
  const completionPath = `${taskCasePath}/complete-onboarding`;
  const completionWrites: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**${completionPath}`, async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    completionWrites.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ id: transitionId, version: 6 });
    if (completionWrites.length === 1) return route.abort("connectionfailed");
    return route.fulfill({ response });
  });
  await completion.getByRole("button", { name: "Selesaikan onboarding", exact: true }).click();
  await expect(completion.getByRole("status")).toContainText("Hasil belum terkonfirmasi.");
  await completion.getByRole("button", { name: "Ulangi tindakan awal", exact: true }).click();
  await expect(completion).toHaveCount(0);
  expect(completionWrites).toHaveLength(2);
  expect(completionWrites[1]).toEqual(completionWrites[0]);
  expect(completionWrites[0]?.body).toEqual({
    expectedVersion: 5,
    reason: "Browser onboarding completed",
  });
  await page.unroute(`**${completionPath}`);
  const completedCase = await (await context.request.get(taskCasePath)).json();
  expect(completedCase).toMatchObject({
    version: 6,
    status: "COMPLETED",
    tasks: [{ key: "equipment", status: "DONE", assigneeId: lifecycleActor.account.id }],
  });
  const completionHistory = await (
    await context.request.get(`${taskCasePath}/history?after=4`)
  ).json();
  expect(completionHistory.items).toHaveLength(2);
  expect(completionHistory.items[0]).toMatchObject({ version: 5, action: "TASK_DONE" });
  expect(completionHistory.items[1]).toMatchObject({
    version: 6,
    action: "COMPLETED",
    reason: "Browser onboarding completed",
  });
  const departingEmployee = randomUUID();
  const departingNumber = `OFFB-${departingEmployee.slice(0, 8).toUpperCase()}`;
  const departingEmployeePath = `/api/v1/companies/${companies[1]}/employees/${departingEmployee}`;
  csrf = await (await context.request.get("/api/v1/auth/csrf")).json();
  const departureEmployee = await context.request.post(
    `/api/v1/companies/${companies[1]}/employees`,
    {
      headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
      data: {
        id: departingEmployee,
        employeeNumber: departingNumber,
        person: { id: randomUUID(), legalName: "Offboarding fixture employee", nationality: "ID" },
        terms: {
          effectiveFrom: "2026-01-01",
          startDate: "2026-01-01",
          status: "ACTIVE",
          contract: "PERMANENT",
        },
        reason: "Browser offboarding review fixture",
      },
    },
  );
  expect(departureEmployee.status()).toBe(200);
  const departureCaseId = randomUUID();
  const departurePath = `${caseCreationPath}/${departureCaseId}`;
  const departureCase = await context.request.post(caseCreationPath, {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: {
      id: departureCaseId,
      employmentId: departingEmployee,
      templateId: offboardingTemplateId,
      templateVersion: 0,
      targetDate: "2026-10-03",
      assignees: {},
      reason: "Browser departure review fixture",
    },
  });
  expect(departureCase.status()).toBe(200);
  for (const [key, status, version] of [
    ["equipment_return", "DONE", 0],
    ["exit_meeting", "WAIVED", 1],
  ] as const) {
    const resolved = await context.request.put(`${departurePath}/tasks/${key}`, {
      headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
      data: { expectedVersion: version, status, reason: "Departure checklist reviewed" },
    });
    expect(resolved.status()).toBe(200);
  }
  await page.getByRole("link", { name: "Proses lifecycle", exact: true }).click();
  await lifecycleCases
    .getByRole("button", {
      name: `Buka proses: Offboarding fixture employee · ${departingNumber}`,
      exact: true,
    })
    .click();
  await page.getByRole("link", { name: "Tinjau offboarding", exact: true }).click();
  await expect(page.getByRole("region", { name: "Tinjau offboarding", exact: true })).toContainText(
    "Versi employment",
  );
  const departureReviewResponse = await context.request.get(`${departurePath}/offboarding-review`);
  expect(departureReviewResponse.status()).toBe(200);
  const departureReview = await departureReviewResponse.json();
  expect(departureReview).toMatchObject({
    case: { id: departureCaseId, version: 2 },
    employmentVersion: 0,
  });
  expect(Object.keys(departureReview).sort()).toEqual(["case", "employmentVersion", "today"]);
  expect(Object.keys(departureReview.case.employee).sort()).toEqual([
    "employeeNumber",
    "id",
    "name",
  ]);
  await page.getByLabel("Alasan", { exact: true }).fill("Browser departure completion");
  const interveningRevision = await context.request.post(`${departingEmployeePath}/revisions`, {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: {
      version: 0,
      terms: {
        effectiveFrom: departureReview.today,
        startDate: "2026-01-01",
        status: "ACTIVE",
        contract: "PERMANENT",
      },
      reason: "Concurrent employment revision before browser completion",
    },
  });
  expect(interveningRevision.status()).toBe(200);
  await page.getByRole("button", { name: "Selesaikan offboarding", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Employment sudah berubah.");
  await page.getByRole("button", { name: "Muat ulang tinjauan", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Tinggalkan halaman?", exact: true })
    .getByRole("button", { name: "Tinggalkan halaman", exact: true })
    .click();
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("");
  await page.getByLabel("Alasan", { exact: true }).fill("Browser updated departure completion");
  const offboardingWrites: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**${departurePath}/complete-offboarding`, async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    offboardingWrites.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ id: departureCaseId, version: 3 });
    if (offboardingWrites.length === 1) return route.abort("connectionfailed");
    return route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Selesaikan offboarding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Hasil belum terkonfirmasi.");
  await page.getByRole("button", { name: "Ulangi penyelesaian awal", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Offboarding selesai.");
  expect(offboardingWrites).toHaveLength(2);
  expect(offboardingWrites[1]).toEqual(offboardingWrites[0]);
  expect(offboardingWrites[0]?.body).toEqual({
    expectedVersion: 2,
    employmentVersion: 1,
    reason: "Browser updated departure completion",
  });
  await page.unroute(`**${departurePath}/complete-offboarding`);
  const endedEmployment = await context.request.get(`${departingEmployeePath}?asOf=2100-01-01`);
  expect(endedEmployment.status()).toBe(200);
  expect(await endedEmployment.json()).toMatchObject({
    version: 2,
    terms: { status: "ENDED", endDate: "2026-10-03" },
  });
  const offboardingHistory = await (
    await context.request.get(`${departurePath}/history?after=2`)
  ).json();
  expect(offboardingHistory.items).toHaveLength(1);
  expect(offboardingHistory.items[0]).toMatchObject({
    version: 3,
    action: "COMPLETED",
    reason: "Browser updated departure completion",
  });
  await page.getByRole("link", { name: "Buka proses", exact: true }).click();
  await expect(page.getByRole("region", { name: "Ringkasan", exact: true })).toContainText(
    "Selesai",
  );
  await expect(page.getByRole("link", { name: "Tinjau offboarding", exact: true })).toHaveCount(0);
  const importPath = `/api/v1/companies/${companies[1]}/employee-imports`;
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
  await page.getByRole("link", { name: "Policy client", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText(
    "Belum ada konfigurasi perusahaan yang disimpan.",
  );
  const policyPath = `/api/v1/companies/${companies[1]}/settings/client-policy`;
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
    expect(await response.json()).toMatchObject({ id: companies[1], version: 1 });
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
      `/api/v1/companies/${companies[1]}/employees?asOf=${employmentEffectiveDate}`,
      {
        headers: { "X-HRIS-Client-Platform": "WEB", "X-HRIS-Client-Build": "1" },
      },
    );
    expect(business.status()).toBe(status);
    if (code) expect(await business.json()).toMatchObject({ code });
  }
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
