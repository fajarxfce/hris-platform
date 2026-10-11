import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import type { ShiftChangeDto, ShiftDto } from "../src/features/workforce/data/models/shift-dto";
import { authenticatorCode } from "./fixtures/authenticator";

test("real shift editing recovers one commit and preserves assigned calendar snapshots", async ({
  page,
  context,
}) => {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("browser-workforce-admin@example.invalid");
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
    data: { code: "BROWSER_SHIFT", name: "Browser Shifts", timezone: "Asia/Jakarta" },
  });
  expect(created.status()).toBe(200);
  const company = ((await created.json()) as { id: string }).id;
  const base = `/api/v1/companies/${company}`;
  await page.reload();
  await page.getByRole("link", { name: "Shifts", exact: true }).click();
  await page.getByRole("link", { name: "Create shift", exact: true }).click();
  await page.getByLabel("Code", { exact: true }).fill(" night ");
  await page.getByLabel("Name", { exact: true }).fill("Night coverage");
  await page.getByLabel("Start time", { exact: true }).fill("22:00");
  await page.getByLabel("End time", { exact: true }).fill("06:00");
  await page.getByLabel("Break (minutes)", { exact: true }).fill("30");
  await page.getByLabel("Reason", { exact: true }).fill("Initial night coverage");
  let lost = false;
  const writes: { id: string; operation: string; body: ShiftChangeDto }[] = [];
  await page.route("**/api/v1/companies/*/workforce/shifts/*", async (route) => {
    const request = route.request();
    if (request.method() !== "PUT") return route.continue();
    writes.push({
      id: new URL(request.url()).pathname.split("/").at(-1) ?? "",
      operation: request.headers()["idempotency-key"] ?? "",
      body: request.postDataJSON() as ShiftChangeDto,
    });
    if (lost) return route.continue();
    lost = true;
    const committed = await route.fetch();
    expect(committed.status()).toBe(200);
    return route.abort("failed");
  });
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Shift saved.");
  expect(writes).toHaveLength(2);
  expect(writes[1]).toEqual(writes[0]);
  const first = writes[0];
  if (!first) throw new Error("Expected the original shift request");
  const shift = first.id;
  const detailResponse = await context.request.get(`${base}/workforce/shifts/${shift}`);
  expect(detailResponse.status()).toBe(200);
  const original = (await detailResponse.json()) as ShiftDto;
  expect(original.version).toBe(0);
  expect(original.details.code).toBe("NIGHT");
  const employee = randomUUID();
  const enrolled = await context.request.post(`${base}/employees`, {
    headers: headers(),
    data: {
      id: employee,
      employeeNumber: "NIGHT-001",
      person: { id: randomUUID(), legalName: "Calendar Fixture Employee", nationality: "ID" },
      terms: {
        effectiveFrom: "2026-01-01",
        startDate: "2026-01-01",
        status: "ACTIVE",
        contract: "PERMANENT",
      },
      reason: "Calendar fixture",
    },
  });
  expect(enrolled.status()).toBe(200);
  const days = Object.fromEntries(
    ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"].map((day) => [
      day,
      { id: shift, version: 0 },
    ]),
  );
  const assigned = await context.request.put(`${base}/workforce/employees/${employee}/schedule`, {
    headers: headers(),
    data: { effectiveFrom: "2026-01-01", days, reason: "Night roster" },
  });
  expect(assigned.status()).toBe(200);
  await page.getByRole("link", { name: "View details", exact: true }).click();
  await page.getByRole("link", { name: "Edit shift", exact: true }).click();
  await page.getByLabel("Start time", { exact: true }).fill("21:00");
  await page.getByLabel("Status", { exact: true }).selectOption("false");
  await page.getByLabel("Reason", { exact: true }).fill("Retire the old template");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Shift saved.");
  expect(writes).toHaveLength(3);
  expect(writes[2]?.body.expectedVersion).toBe(0);
  expect(writes[2]?.operation).not.toBe(first.operation);
  const changed = (await (
    await context.request.get(`${base}/workforce/shifts/${shift}`)
  ).json()) as ShiftDto;
  expect(changed.version).toBe(1);
  expect(changed.active).toBe(false);
  const calendarResponse = await context.request.get(
    `${base}/workforce/employees/${employee}/calendar?from=2026-10-11&until=2026-10-11`,
  );
  expect(calendarResponse.status()).toBe(200);
  const calendar = (await calendarResponse.json()) as {
    days: { startsAt: string; plannedMinutes: number; shift: { id: string; revision: number } }[];
  };
  expect(calendar.days).toHaveLength(1);
  expect(calendar.days[0]).toMatchObject({
    startsAt: "2026-10-11T15:00:00Z",
    plannedMinutes: 450,
    shift: { id: shift, revision: 0 },
  });
});
