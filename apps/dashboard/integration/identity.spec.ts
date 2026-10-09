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

test("real API sessions, MFA, company reports, locale errors and logout", async ({
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
  const created = await context.request.post(`/api/v1/companies/${companies[1]}/employees`, {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: {
      id: randomUUID(),
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
