import { expect, test } from "@playwright/test";
import { leaveEmployeeId, leaveId } from "./fixtures/leave";
import { balanceEntryId, balanceTypeId } from "./fixtures/leave-balances";
import { companyIds } from "./identity-api";
import { installLeaveBalancesApi } from "./leave-balances-api";

const base = `/leave/employees/${leaveEmployeeId()}/balances`;
const query = `company=${companyIds[0]}&year=2026`;
const directory = `${base}?${query}`;
const ledger = `${base}/${balanceTypeId()}?${query}`;

test("balance pages retain employee, year and directory cursor across detail and browser navigation", async ({
  page,
}) => {
  const api = await installLeaveBalancesApi(page);
  await page.goto(directory);
  const table = page.getByRole("table", { name: "Leave balances", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(4);
  await table.getByRole("button", { name: "View ledger: Leave type 23", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toContainText(
    "Leave type 23",
  );
  await page.getByRole("link", { name: "Back to balances", exact: true }).click();
  await expect(page).toHaveURL(`${directory}&after=TYPE020`);
  await expect(table.getByRole("row")).toHaveCount(4);
  const reads = api.reads.length;
  await page.getByLabel("Balance year", { exact: true }).fill("2025");
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page).toHaveURL(`${base}?company=${companyIds[0]}&year=2025`);
  await expect(table.getByRole("row").nth(1)).toContainText("Closed");
  await page.goBack();
  await expect(table.getByRole("row")).toHaveCount(4);
  await expect(page.getByLabel("Balance year", { exact: true })).toHaveValue("2026");
  await page.goForward();
  await expect(page.getByLabel("Balance year", { exact: true })).toHaveValue("2025");
  await page.getByLabel("Balance year", { exact: true }).fill("2027");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No balance accounts for this year");
});

test("ledger selection shows original evidence while older pages retain the current balance", async ({
  page,
}) => {
  const api = await installLeaveBalancesApi(page);
  await page.goto(ledger);
  const table = page.getByRole("table", { name: "Balance movements (Asia/Jakarta)", exact: true });
  const summary = page.getByRole("region", { name: "Current balance", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  await expect(summary).toContainText("8.5");
  await page
    .getByRole("button", { name: `View movement: Consumption · ${balanceEntryId()}`, exact: true })
    .click();
  const movement = page.getByRole("region", { name: "Selected movement", exact: true });
  await expect(movement).toBeFocused();
  await expect(movement).toContainText("Final leave approval");
  const reads = api.reads.length;
  await expect(page.getByRole("link", { name: "View leave request", exact: true })).toHaveAttribute(
    "href",
    `/leave/requests/${leaveId()}?company=${companyIds[0]}&employee=${leaveEmployeeId()}`,
  );
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Older movements", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(4);
  await expect(movement).toHaveCount(0);
  await expect(summary).toContainText("8.5");
  await page
    .getByRole("button", { name: `View movement: Adjustment · ${balanceEntryId(22)}`, exact: true })
    .click();
  await expect(movement).toContainText("Balance correction 22");
  await expect(page.getByRole("link", { name: "View leave request", exact: true })).toHaveCount(0);
  await page.goBack();
  await expect(table.getByRole("row")).toHaveCount(21);
  await expect(movement).toHaveCount(0);
});

test("a scoped reader reaches the charged year's ledger from a leave request without policy or people administration", async ({
  page,
}) => {
  const api = await installLeaveBalancesApi(page, ["leave.self.manage"]);
  await page.goto(`/leave/requests/${leaveId()}?company=${companyIds[0]}`);
  await page.getByRole("link", { name: "Balance ledger · 2026", exact: true }).click();
  await expect(page).toHaveURL(ledger);
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toContainText(
    "8.5",
  );
  expect(api.identity.unhandled).toEqual([]);
  await expect(page.getByRole("link", { name: "Leave policies", exact: true })).toHaveCount(0);
  await page
    .getByRole("button", { name: `View movement: Consumption · ${balanceEntryId()}`, exact: true })
    .click();
  await page.getByRole("link", { name: "View leave request", exact: true }).click();
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toBeVisible();
});

test("employee details expose balances for the selected employment year", async ({ page }) => {
  await installLeaveBalancesApi(page, ["people.read", "leave.read"]);
  await page.route(
    `**/api/v1/companies/${companyIds[0]}/employees/${leaveEmployeeId()}?*`,
    (route) =>
      route.fulfill({
        json: {
          id: leaveEmployeeId(),
          companyId: companyIds[0],
          employeeNumber: "EMP-0-1",
          person: { legalName: "North Employee 01", email: null },
          terms: {
            effectiveFrom: "2025-01-01",
            startDate: "2025-01-01",
            endDate: null,
            status: "ACTIVE",
            contract: "PERMANENT",
          },
          version: 0,
          appliedRevision: 0,
        },
      }),
  );
  await page.goto(
    `/people/employees/${leaveEmployeeId()}?company=${companyIds[0]}&asOf=2026-09-30`,
  );
  await page.getByRole("link", { name: "Leave balances", exact: true }).click();
  await expect(page).toHaveURL(directory);
  await expect(page.getByRole("region", { name: "Employee", exact: true })).toContainText(
    "North Employee 01",
  );
});

for (const kind of ["list", "ledger"] as const)
  test(`company replacement cancels pending ${kind} data and a late assurance failure`, async ({
    page,
  }) => {
    const api = await installLeaveBalancesApi(page);
    await page.goto(
      kind === "list" ? `${directory}&after=TYPE020` : `${ledger}&after=${balanceEntryId(20)}`,
    );
    await expect(
      page.getByRole("table", {
        name: kind === "list" ? "Leave balances" : "Balance movements (Asia/Jakarta)",
        exact: true,
      }),
    ).toBeVisible();
    // Start the controlled wait after StrictMode's mount/cleanup cycle has settled.
    const held = api.hold(kind);
    try {
      await page.getByRole("button", { name: "Refresh", exact: true }).click();
      await held.entered;
      const reads = api.reads.length;
      api.failNext("mfa_required");
      await page
        .getByRole("combobox", { name: "Company", exact: true })
        .selectOption(companyIds[1]);
      await expect(page).toHaveURL("/");
      held.release();
      await expect(page.getByRole("main")).not.toContainText("North Employee");
      await expect(
        page.getByRole("dialog", { name: "Account verification", exact: true }),
      ).toHaveCount(0);
      expect(api.reads).toHaveLength(reads);
    } finally {
      held.release();
    }
  });

test("invalid years and movement cursors remain local and balance permission is separate from policy management", async ({
  page,
}) => {
  const api = await installLeaveBalancesApi(page);
  await page.goto(`${base}?company=${companyIds[0]}&year=2026.0`);
  await expect(page.getByRole("alert")).toContainText("This page link is invalid");
  await page.goto(`${ledger}&after=invalid`);
  await expect(page.getByRole("alert")).toContainText("This page link is invalid");
  expect(api.reads).toHaveLength(0);
  api.identity.setPermissions(["leave.manage"]);
  await page.goto(directory);
  await expect(page.getByRole("alert")).toContainText("You do not have access to this resource");
  expect(api.reads).toHaveLength(0);
});

for (const path of [directory, ledger])
  test(`malformed balance context is never rendered at ${path}`, async ({ page }) => {
    const api = await installLeaveBalancesApi(page);
    api.malformed();
    await page.goto(path);
    await expect(page.getByRole("alert")).toContainText("response");
    await expect(page.getByRole("main")).not.toContainText("North Employee 01");
  });

test("revoked ledger access clears the balance and selection before explicit recovery", async ({
  page,
}) => {
  const api = await installLeaveBalancesApi(page);
  await page.goto(ledger);
  await page
    .getByRole("button", { name: `View movement: Consumption · ${balanceEntryId()}`, exact: true })
    .click();
  api.failNext("employee_not_found");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("Final leave approval");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE SERVER DETAIL");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toBeVisible();
});

test("MFA renewal retains the balance route without automatically rereading private evidence", async ({
  page,
}) => {
  const api = await installLeaveBalancesApi(page);
  await page.goto(ledger);
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toBeVisible();
  api.identity.expireMfa();
  api.failNext("mfa_required");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const reads = api.reads.length;
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toBeVisible();
});

test("ledger evidence supports Indonesian dark mobile layout without restarting I/O", async ({
  page,
}) => {
  const api = await installLeaveBalancesApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(ledger);
  await page
    .getByRole("button", { name: `View movement: Consumption · ${balanceEntryId()}`, exact: true })
    .click();
  const reads = api.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("button", { name: "Muat ulang", exact: true })).toHaveCSS(
    "color",
    "rgb(255, 255, 255)",
  );
  await expect(page.getByRole("region", { name: "Saldo terkini", exact: true })).toContainText(
    "8,5",
  );
  await expect(
    page.getByRole("region", { name: "Detail transaksi", exact: true }).first(),
  ).toContainText("Final leave approval");
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-leave-balances-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});
