import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { companyDate } from "../src/core/presentation/dates/company-date";
import { authenticatorCode } from "./fixtures/authenticator";

test("real API initial funding recovers one receipt and a competing correction requires a fresh balance version", async ({
  page,
  context,
}) => {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("browser-balance-admin@example.invalid");
  await page.getByLabel("Password", { exact: true }).fill("Browser-fixture-password-123!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("button", { name: "Set up an authenticator", exact: true }).click();
  const key = page.locator(".app-enrollment-key code");
  await expect(key).toBeVisible();
  await page
    .getByLabel("Authenticator code", { exact: true })
    .fill(authenticatorCode((await key.textContent()) ?? ""));
  await page.getByRole("button", { name: "Verify", exact: true }).click();
  await page.getByRole("button", { name: "I have saved the codes", exact: true }).click();
  await expect(page.getByRole("heading", { name: "No company access", exact: true })).toBeVisible();
  const csrf = (await (await context.request.get("/api/v1/auth/csrf")).json()) as {
    headerName: string;
    token: string;
  };
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const created = await context.request.post("/api/v1/companies", {
    headers: headers(),
    data: { code: "BALANCEBROWSER", name: "Browser Balances", timezone: "Asia/Jakarta" },
  });
  expect(created.status()).toBe(200);
  const company = ((await created.json()) as { id: string }).id;
  const base = `/api/v1/companies/${company}`;
  const employee = randomUUID();
  const type = randomUUID();
  expect(
    (
      await context.request.post(`${base}/employees`, {
        headers: headers(),
        data: {
          id: employee,
          employeeNumber: "BALANCE-EMPLOYEE",
          person: { id: randomUUID(), legalName: "Browser Balance Employee", nationality: "ID" },
          terms: {
            effectiveFrom: "2025-01-01",
            startDate: "2025-01-01",
            status: "ACTIVE",
            contract: "PERMANENT",
          },
          reason: "Balance browser setup",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.put(`${base}/leave/types/${type}`, {
        headers: headers(),
        data: {
          code: "BALANCELEAVE",
          name: "Browser balance leave",
          effectiveFrom: "2025-01-01",
          paid: true,
          allowPartialDays: true,
          reason: "Balance browser policy",
        },
      })
    ).status(),
  ).toBe(200);
  const year = companyDate("Asia/Jakarta", new Date()).slice(0, 4);
  const path = `/leave/employees/${employee}/balances`;
  const resource = `${base}/leave/employees/${employee}/balances/${type}/${year}`;
  await page.goto(`${path}?company=${company}&year=${year}`);
  await expect(page.getByRole("status")).toContainText("No balance accounts for this year");
  await page.getByRole("link", { name: "Adjust leave balance", exact: true }).click();
  await page
    .getByRole("button", { name: "Review balance: Browser balance leave", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toContainText(
    "No movements recorded",
  );
  await page.getByLabel("Change in days", { exact: true }).fill("12.5");
  await page.getByLabel("Reason", { exact: true }).fill("Initial balance reviewed");
  const commands: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**${resource}/adjustments`, async (route) => {
    commands.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    if (commands.length === 1) return route.abort("failed");
    return route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The outcome is not confirmed");
  await page.getByRole("button", { name: "Retry adjustment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Adjustment recorded.");
  expect(commands).toHaveLength(2);
  expect(commands[0]).toEqual(commands[1]);
  await page.unroute(`**${resource}/adjustments`);
  const initial = await (await context.request.get(resource)).json();
  expect(initial).toMatchObject({
    balance: { availableDays: "12.5", version: 1 },
    availableActions: ["ADJUST"],
  });
  expect(initial.entries.items).toHaveLength(1);
  expect(initial.entries.items[0]).toMatchObject({
    kind: "ADJUSTMENT",
    reason: "Initial balance reviewed",
    availableDeltaDays: "12.5",
  });
  await page.getByRole("link", { name: "View current balance", exact: true }).click();
  await page.getByRole("link", { name: "Adjust leave balance", exact: true }).click();
  await page.getByLabel("Change in days", { exact: true }).fill("-0.5");
  await page.getByLabel("Reason", { exact: true }).fill("Corrected allocation");
  expect(
    (
      await context.request.post(`${resource}/adjustments`, {
        headers: headers(),
        data: { days: "1", expectedVersion: 1, reason: "Competing reviewed allocation" },
      })
    ).status(),
  ).toBe(200);
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("The balance changed");
  await expect(page.getByRole("button", { name: "Record adjustment", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toContainText(
    "13.5",
  );
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Corrected allocation");
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Adjustment recorded.");
  const corrected = await (await context.request.get(resource)).json();
  expect(corrected).toMatchObject({ balance: { availableDays: "13", version: 3 } });
  expect(corrected.entries.items).toHaveLength(3);
  expect(corrected.entries.items[0]).toMatchObject({
    kind: "ADJUSTMENT",
    availableDeltaDays: "-0.5",
    reason: "Corrected allocation",
  });
  expect(corrected.entries.items[2]).toEqual(initial.entries.items[0]);
  await page.getByRole("link", { name: "View current balance", exact: true }).click();
  await page
    .getByRole("button", {
      name: `View movement: Adjustment · ${corrected.entries.items[0].id}`,
      exact: true,
    })
    .click();
  await expect(page.getByRole("region", { name: "Selected movement", exact: true })).toContainText(
    "Corrected allocation",
  );
});
