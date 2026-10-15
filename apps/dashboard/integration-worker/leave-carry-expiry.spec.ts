import { expect, test } from "@playwright/test";
import { signInAndEnrollAuthenticator } from "../integration/fixtures/sign-in-and-enroll-authenticator";
import type { LeaveCarryExpiryDetailsDto } from "../src/features/leave/data/models/leave-carry-expiry-dto";
import type { LeaveLedgerDto } from "../src/features/leave/data/models/leave-ledger-dto";

const company = "b0000000-0000-4000-8000-000000000062",
  expiry = "e0000000-0000-4000-8000-000000000063",
  employee = "d0000000-0000-4000-8000-000000000062",
  type = "f0000000-0000-4000-8000-000000000062",
  base = `/api/v1/companies/${company}`;

test("retained carry recovery survives response loss and the real worker debits it once", async ({
  page,
  context,
}) => {
  await signInAndEnrollAuthenticator(page, "browser-carry-operator@example.invalid");
  const read = async () => {
    const response = await context.request.get(`${base}/leave/carry-expiries/${expiry}`);
    expect(response.status()).toBe(200);
    const value = (await response.json()) as LeaveCarryExpiryDetailsDto;
    await response.dispose();
    return value;
  };
  const original = await read();
  expect(original.expiry.status).toBe("FAILED");
  expect(original.expiry.completedEmployees).toBe(0);
  expect(original.availableActions).toEqual(["RESUME"]);
  await page.goto(`/leave/carry-expiries?company=${company}`);
  await page
    .getByRole("button", {
      name: `View expiry: Retained annual leave · ${original.expiry.year}`,
      exact: true,
    })
    .click();
  await expect(page.getByRole("region", { name: "Process details", exact: true })).toContainText(
    "0 / 1",
  );
  await page.getByRole("link", { name: "Resume expiry", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed remaining carry expiry");
  const commands: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**${base}/leave/carry-expiries/${expiry}/resume`, async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    commands.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    if (commands.length === 1) {
      await response.dispose();
      await route.abort("failed");
    } else {
      await route.fulfill({ response });
      await response.dispose();
    }
  });
  await page.getByRole("button", { name: "Resume expiry", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  await expect.poll(async () => (await read()).job.status, { timeout: 30_000 }).toBe("SUCCEEDED");
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Expiry process resumed.");
  expect(commands).toHaveLength(2);
  expect(commands[0]?.operation).toBeTruthy();
  expect(commands[1]).toEqual(commands[0]);
  const completed = await read();
  expect(completed.expiry).toMatchObject({
    id: expiry,
    status: "SUCCEEDED",
    completedEmployees: 1,
    version: 1,
    typeName: "Retained annual leave",
    validThrough: original.expiry.validThrough,
    expiresAt: original.expiry.expiresAt,
  });
  expect(completed.job.id).not.toBe(original.job.id);
  expect(completed.attempts).toHaveLength(2);
  expect(completed.job.completedItems).toBe(2);
  expect(completed.results.items).toHaveLength(1);
  expect(completed.results.items[0]).toMatchObject({
    employeeId: employee,
    status: "EXPIRED",
    expiredDays: "3",
  });
  const ledgerResponse = await context.request.get(
    `${base}/leave/employees/${employee}/balances/${type}/${completed.expiry.year}`,
  );
  expect(ledgerResponse.status()).toBe(200);
  const ledger = (await ledgerResponse.json()) as LeaveLedgerDto;
  await ledgerResponse.dispose();
  const debits = ledger.entries.items.filter((item) => item.kind === "EXPIRE");
  expect(debits).toHaveLength(1);
  expect(debits[0]).toMatchObject({
    id: completed.results.items[0]?.ledgerId,
    availableDeltaDays: "-3",
  });
  expect(ledger.balance.availableDays).toBe("0");
  expect(ledger.balance.carry).toMatchObject({
    awardedDays: "3",
    availableDays: "0",
    reservedDays: "0",
    consumedDays: "0",
    expiredDays: "3",
    transferredDays: "0",
    deductedDays: "0",
    validThrough: original.expiry.validThrough,
    expiresAt: original.expiry.expiresAt,
    timezone: original.expiry.timezone,
  });
  await page.getByRole("link", { name: "View expiry", exact: true }).click();
  const results = page.getByRole("table", { name: "Employee results", exact: true });
  await expect(results).toContainText("Carry Fixture Employee");
  await expect(results).toContainText("Expired");
  await expect(page.getByRole("link", { name: "Resume expiry", exact: true })).toHaveCount(0);
  await expect(
    page.getByRole("table", { name: "Process attempts", exact: true }).getByRole("row"),
  ).toHaveCount(3);
  await page.screenshot({ path: "/tmp/hris-carry-expiry-ui/real-worker.png", fullPage: true });
  await results
    .getByRole("button", { name: "View balance: Carry Fixture Employee", exact: true })
    .click();
  await expect(page).toHaveURL(new RegExp(`/balances/${type}\\?`, "u"));
  expect(new URL(page.url()).searchParams.get("year")).toBe(String(completed.expiry.year));
  const carry = page.getByRole("region", { name: "Carried balance", exact: true });
  await expect(carry).toBeVisible();
  await expect(carry.getByText("Expired days", { exact: true }).locator("+ dd")).toHaveText("3");
  await expect(carry.getByText("Available days", { exact: true }).locator("+ dd")).toHaveText("0");
  await page.screenshot({ path: "/tmp/hris-carry-balance-ui/real-worker.png", fullPage: true });
});
