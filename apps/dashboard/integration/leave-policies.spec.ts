import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { companyDate } from "../src/core/presentation/dates/company-date";
import { authenticatorCode } from "./fixtures/authenticator";

test("real API policy creation and future revisions retain history and replay a lost save", async ({
  page,
  context,
}) => {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("browser-policy-admin@example.invalid");
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
  const created = await context.request.post("/api/v1/companies", {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: { code: "POLICYBROWSER", name: "Browser Policies", timezone: "Asia/Jakarta" },
  });
  expect(created.status()).toBe(200);
  const company = ((await created.json()) as { id: string }).id;
  const other = await context.request.post("/api/v1/companies", {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: { code: "POLICYOTHER", name: "Browser Other Policy Company", timezone: "Asia/Jakarta" },
  });
  expect(other.status()).toBe(200);
  const otherCompany = ((await other.json()) as { id: string }).id;
  await page.reload();
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
  await page.getByRole("link", { name: "Leave policies", exact: true }).click();
  await page.getByRole("link", { name: "Create policy", exact: true }).click();
  await page.getByLabel("Code", { exact: true }).fill("browserpolicy");
  await page.getByLabel("Name", { exact: true }).fill("Browser annual policy");
  const today = companyDate("Asia/Jakarta", new Date());
  await page.getByLabel("Effective from", { exact: true }).fill(today);
  await page.getByRole("combobox", { name: "Entitlement", exact: true }).selectOption("ANNUAL");
  await page.getByLabel("Days per period", { exact: true }).fill("12.5");
  await page.getByLabel("Carryover limit (days)", { exact: true }).fill("3.5");
  await page.getByLabel("Reason", { exact: true }).fill("Browser policy creation");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  await page.getByRole("link", { name: "View policy", exact: true }).click();
  await expect(page.getByRole("region", { name: "Policy terms", exact: true })).toContainText(
    "12.5",
  );
  const id = new URL(page.url()).pathname.split("/").at(-1);
  const endpoint = `/api/v1/companies/${company}/leave/policies/${id}`;
  const original = await (await context.request.get(endpoint)).json();
  expect(original).toMatchObject({
    current: { code: "BROWSERPOLICY", version: 0 },
    history: { items: [{ revision: 0 }] },
  });
  expect(
    (await context.request.get(`/api/v1/companies/${otherCompany}/leave/policies/${id}`)).status(),
  ).toBe(404);
  await page.getByRole("link", { name: "Edit policy", exact: true }).click();
  await expect(page.getByLabel("Code", { exact: true })).toHaveAttribute("readonly", "");
  await page.getByLabel("Name", { exact: true }).fill("Future annual policy");
  await page
    .getByLabel("Effective from", { exact: true })
    .fill(`${Number(today.slice(0, 4)) + 1}-01-01`);
  await page.getByLabel("Reason", { exact: true }).fill("Future allowance revision");
  const commands: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**/api/v1/companies/${company}/leave/types/${id}`, async (route) => {
    expect(route.request().method()).toBe("PUT");
    commands.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    if (commands.length === 1) return route.abort("failed");
    return route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(commands).toHaveLength(2);
  expect(commands[0]).toEqual(commands[1]);
  const current = await (await context.request.get(endpoint)).json();
  expect(current).toMatchObject({
    current: { name: "Future annual policy", version: 1 },
    history: { items: [{ revision: 1 }, { revision: 0 }] },
  });
  expect(current.history.items).toHaveLength(2);
  expect(current.history.items[1]).toEqual(original.history.items[0]);
  const effective = await (
    await context.request.get(`/api/v1/companies/${company}/leave/types?asOf=${today}`)
  ).json();
  expect(effective.items).toEqual([
    expect.objectContaining({ id, name: "Browser annual policy", version: 1, appliedRevision: 0 }),
  ]);
  await page.getByRole("link", { name: "View policy", exact: true }).click();
  await page.getByRole("button", { name: "View: Revision 0", exact: true }).click();
  await expect(page.getByRole("region", { name: "Revision 0", exact: true })).toContainText(
    "Browser annual policy",
  );
});
