import { expect, type Page, test } from "@playwright/test";
import { installClientPolicyEditingApi } from "./client-policy-editing-api";
import { companyIds } from "./identity-api";

const policyPath = `/settings/client-policy?company=${companyIds[0]}`;
const editorPath = `/settings/client-policy/edit?company=${companyIds[0]}`;
const save = (page: Page) => page.getByRole("button", { name: "Save policy", exact: true });
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });
async function open(page: Page) {
  await page.goto(editorPath);
  await expect(
    page.getByRole("heading", { name: "Edit client policy", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill("Mobile rollout");
}

test("editing from a historical revision loads the configured head and retains UTC values across locale and theme changes", async ({
  page,
}) => {
  const api = await installClientPolicyEditingApi(page);
  await page.goto(`${policyPath}&version=0`);
  await expect(
    page.getByRole("region", { name: "Configuration revision", exact: true }),
  ).toContainText("Existing policy 0");
  await page.getByRole("link", { name: "Edit latest policy", exact: true }).click();
  await expect(page.getByLabel("Activation", { exact: true })).toHaveValue("SCHEDULED");
  await expect(page.getByLabel("Activate at (UTC)", { exact: true })).toHaveValue(
    "2026-11-01T00:00:00.123456Z",
  );
  await expect(page.getByRole("checkbox", { name: "Payroll", exact: true })).not.toBeChecked();
  await expect(page.getByRole("checkbox", { name: "Expenses", exact: true })).toBeChecked();
  await expect(page.getByLabel("Android", { exact: true })).toHaveValue("42");
  await expect(page.getByRole("main")).toContainText("Configured revision: 1");
  await expect(page.getByRole("main")).toContainText("Effective revision: 0");
  await page.getByLabel("Android", { exact: true }).fill("43");
  await page.getByLabel("Web", { exact: true }).fill("2");
  await expect(
    page.getByText("This minimum exceeds the current dashboard build.", { exact: false }),
  ).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill("  Mobile rollout  ");
  const reads = api.reads.length;
  await page.screenshot({
    path: "../../.work/dashboard-client-policy-editor-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.screenshot({
    path: "../../.work/dashboard-client-policy-editor-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("  Mobile rollout  ");
  await expect(page.getByLabel("Mulai maintenance (UTC)", { exact: true })).toHaveValue(
    "2026-11-01T01:00:00.123456Z",
  );
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-client-policy-editor-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Simpan policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy tersimpan.");
  expect(api.writes).toHaveLength(1);
  expect(api.writes[0]?.body).toEqual({
    expectedVersion: 1,
    activateAt: "2026-11-01T00:00:00.123456Z",
    disabledModules: ["PAYROLL"],
    minimumBuilds: { android: 43, ios: 0, web: 2 },
    maintenance: { startsAt: "2026-11-01T01:00:00.123456Z", endsAt: "2026-11-01T01:30:00.123456Z" },
    reason: "Mobile rollout",
  });
  await page.getByRole("link", { name: "Lihat revisi tersimpan", exact: true }).click();
  await expect(page.getByRole("region", { name: "Revisi konfigurasi", exact: true })).toContainText(
    "Mobile rollout",
  );
  await expect(page).toHaveURL(new RegExp(`company=${companyIds[0]}&version=2$`, "u"));
  const modules = page.getByRole("table", { name: "Modul", exact: true });
  await expect(modules.getByRole("row").filter({ hasText: "Pengeluaran" })).toContainText(
    "Nonaktif",
  );
  await expect(modules.getByRole("row").filter({ hasText: "Payroll" })).toContainText("Aktif");
  expect(api.commits).toBe(1);
  expect(api.identity.unhandled).toEqual([]);
});

test("the first company policy starts with explicit defaults and requires only settings management", async ({
  page,
}) => {
  const api = await installClientPolicyEditingApi(page, {
    empty: true,
    permissions: ["settings.manage"],
  });
  await open(page);
  await expect(page.getByLabel("Activation", { exact: true })).toHaveValue("IMMEDIATE");
  await expect(
    page.getByRole("checkbox", { name: "Schedule maintenance", exact: true }),
  ).not.toBeChecked();
  await expect(page.getByLabel("Android", { exact: true })).toHaveValue("0");
  await page.getByRole("checkbox", { name: "Expenses", exact: true }).uncheck();
  await save(page).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes[0]?.body).toMatchObject({
    expectedVersion: null,
    activateAt: null,
    disabledModules: ["EXPENSES"],
    maintenance: null,
    minimumBuilds: { android: 0, ios: 0, web: 0 },
  });
  await page.getByRole("link", { name: "View saved revision", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Configuration revision", exact: true }),
  ).toContainText("Mobile rollout");
  await expect(page).toHaveURL(/version=0$/u);
  expect(api.identity.unhandled).toEqual([]);
});

test("an uncertain scheduled save survives MFA and expired activation replies until its original receipt is recovered", async ({
  page,
}) => {
  const api = await installClientPolicyEditingApi(page);
  await open(page);
  api.loseNext();
  await save(page).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed.");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
  await expect(page.getByRole("checkbox", { name: "Payroll", exact: true })).toBeDisabled();
  api.identity.expireMfa();
  api.rejectNext("recent_authentication_required", 403);
  await page.getByRole("button", { name: "Retry original save", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Mobile rollout");
  expect(api.writes).toHaveLength(2);
  api.setNow("2026-11-02T00:00:00Z");
  api.rejectNext("client_policy_activation_expired");
  await page.getByRole("button", { name: "Retry original save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed.");
  await page.getByRole("button", { name: "Retry original save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes).toHaveLength(4);
  expect(
    api.writes.every(
      (write) =>
        write.operation === api.writes[0]?.operation &&
        JSON.stringify(write.body) === JSON.stringify(api.writes[0]?.body),
    ),
  ).toBe(true);
  expect(api.writes[3]?.csrf).not.toBe(api.writes[0]?.csrf);
  expect(api.commits).toBe(1);
  api.failReads("connection_unavailable");
  await page.getByRole("link", { name: "View saved revision", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Unable to connect");
  expect(api.writes).toHaveLength(4);
});

test("a definite conflict requires protected reloading and a new observed revision before saving", async ({
  page,
}) => {
  const api = await installClientPolicyEditingApi(page);
  await open(page);
  api.advanceHead();
  await save(page).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await expect(save(page)).toBeDisabled();
  await page.getByRole("button", { name: "Reload configuration", exact: true }).click();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Mobile rollout");
  await page.getByRole("button", { name: "Reload configuration", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await expect(page.getByLabel("Android", { exact: true })).toHaveValue("77");
  await page.getByLabel("Reason", { exact: true }).fill("Replace concurrent head");
  await save(page).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([1, 2]);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
  expect(api.commits).toBe(1);
});

test("invalid fields stay editable and definite server failures do not cause hidden retries", async ({
  page,
}) => {
  const api = await installClientPolicyEditingApi(page);
  await open(page);
  await page.getByLabel("Android", { exact: true }).fill("");
  await page
    .getByLabel("Maintenance ends (UTC)", { exact: true })
    .fill("2026-11-01T01:00:00.123456Z");
  await save(page).click();
  await expect(
    page.getByText("Enter a whole number from 0 to 999,999,999.", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("The end must follow the start, within seven days.", { exact: true }),
  ).toBeVisible();
  expect(api.writes).toEqual([]);
  await page.getByLabel("Android", { exact: true }).fill("43");
  await page.getByRole("checkbox", { name: "Schedule maintenance", exact: true }).uncheck();
  api.rejectNext("client_policy_clock_regressed");
  await save(page).click();
  await expect(page.getByRole("alert")).toContainText("The server clock must recover");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Mobile rollout");
  await expect(page.getByLabel("Reason", { exact: true })).not.toHaveAttribute("readonly", "");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE POLICY DETAILS");
  expect(api.writes).toHaveLength(1);
  await save(page).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes[1]?.body.maintenance).toBeNull();
});

test("direct editor links do not acquire settings without permission and never load a foreign company", async ({
  page,
}) => {
  const denied = await installClientPolicyEditingApi(page, { permissions: ["company.read"] });
  await page.goto(editorPath);
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  expect(denied.reads).toEqual([]);
  expect(denied.writes).toEqual([]);
  const api = await installClientPolicyEditingApi(page);
  await page.goto(`/settings/client-policy/edit?company=${companyIds[1]}`);
  await expect(page).toHaveURL(
    new RegExp(`/settings/client-policy\\?company=${companyIds[0]}$`, "u"),
  );
  await expect(
    page.getByRole("region", { name: "Configuration revision", exact: true }),
  ).toBeVisible();
  expect(api.reads.every((url) => url.pathname.includes(companyIds[0] ?? ""))).toBe(true);
  api.advanceHead(9999);
  await page.getByRole("link", { name: "Edit latest policy", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "The company has reached its policy revision limit.",
  );
  await expect(save(page)).toBeDisabled();
  expect(api.writes).toEqual([]);
});

test("company departure owns pending saves and cannot restore the former policy after a late commit", async ({
  page,
}) => {
  const api = await installClientPolicyEditingApi(page);
  await open(page);
  const gate = api.holdNextWrite();
  await save(page).click();
  await gate.entered;
  try {
    await page
      .getByRole("combobox", { name: "Company", exact: true })
      .selectOption(companyIds[1] ?? "");
    await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
    await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Mobile rollout");
    await page
      .getByRole("combobox", { name: "Company", exact: true })
      .selectOption(companyIds[1] ?? "");
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Client policy", exact: true })).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}$`, "u"));
  } finally {
    gate.release();
  }
  await expect.poll(() => api.commits).toBe(1);
  await expect(page.getByRole("main")).not.toContainText("Mobile rollout");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
});

test("credential revocation clears an uncertain policy and its reason", async ({ page }) => {
  const api = await installClientPolicyEditingApi(page);
  await open(page);
  api.loseNext();
  await save(page).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed.");
  api.identity.revoke();
  api.rejectNext("session_revoked", 401);
  await page.getByRole("button", { name: "Retry original save", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  await expect(page.locator("body")).not.toContainText("Mobile rollout");
});
