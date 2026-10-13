import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { companyDate } from "../src/core/presentation/dates/company-date";
import { authenticatorCode } from "./fixtures/authenticator";

test("real API expense policies keep scheduled definitions, immutable history and one recovered save", async ({
  page,
  context,
}) => {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("browser-expense-admin@example.invalid");
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
    data: { code: "EXPENSEBROWSER", name: "Browser Expenses", timezone: "Asia/Jakarta" },
  });
  expect(created.status()).toBe(200);
  const company = ((await created.json()) as { id: string }).id;
  const other = await context.request.post("/api/v1/companies", {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: { code: "EXPENSEOTHER", name: "Other Expense Company", timezone: "Asia/Jakarta" },
  });
  expect(other.status()).toBe(200);
  const otherCompany = ((await other.json()) as { id: string }).id;
  await page.reload();
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
  await page.getByRole("link", { name: "Expense policies", exact: true }).click();
  await page.getByRole("link", { name: "Create category", exact: true }).click();
  const today = companyDate("Asia/Jakarta", new Date()),
    future = `${Number(today.slice(0, 4)) + 1}-01-01`;
  await page.getByLabel("Code", { exact: true }).fill("browser_travel");
  await page.getByLabel("Name", { exact: true }).fill("Business travel");
  await page.getByLabel("Effective from", { exact: true }).fill(today);
  await page.getByLabel("Maximum per line (IDR)", { exact: true }).fill("1250000.25");
  await page.getByLabel("Maximum per claim (IDR)", { exact: true }).fill("5000000.75");
  await page.getByRole("checkbox", { name: "Cost center required", exact: true }).check();
  await page.getByLabel("Reason", { exact: true }).fill("Travel reimbursement policy");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-expense-real-editor.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  await page.getByRole("link", { name: "View policy", exact: true }).click();
  await expect(page.getByRole("region", { name: "Claim requirements", exact: true })).toContainText(
    "1,250,000.25",
  );
  const id = new URL(page.url()).pathname.split("/").at(-1),
    endpoint = `/api/v1/companies/${company}/expenses/policies/${id}`;
  expect(
    (
      await context.request.get(`/api/v1/companies/${otherCompany}/expenses/policies/${id}`)
    ).status(),
  ).toBe(404);
  await page.getByRole("link", { name: "Edit policy", exact: true }).click();
  await expect(page.getByLabel("Code", { exact: true })).toHaveAttribute("readonly", "");
  await page.getByLabel("Name", { exact: true }).fill("Scheduled travel archive");
  await page.getByLabel("Effective from", { exact: true }).fill(future);
  await page.getByRole("checkbox", { name: "Active", exact: true }).uncheck();
  await page.getByLabel("Reason", { exact: true }).fill("Scheduled policy retirement");
  const commands: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**/api/v1/companies/${company}/expenses/categories/${id}`, async (route) => {
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
  const response = await context.request.get(endpoint);
  expect(response.status()).toBe(200);
  const review = await response.json();
  expect(review).toMatchObject({
    current: {
      code: "BROWSER_TRAVEL",
      name: "Scheduled travel archive",
      version: 1,
      appliedRevision: 1,
      active: false,
    },
    history: {
      items: [
        {
          category: {
            appliedRevision: 0,
            name: "Business travel",
            maximumLineAmount: "1250000.25",
          },
        },
        { category: { appliedRevision: 1, active: false } },
      ],
    },
  });
  expect(review.history.items).toHaveLength(2);
  const effective = await (
    await context.request.get(`/api/v1/companies/${company}/expenses/categories?asOf=${today}`)
  ).json();
  expect(effective.items).toEqual([
    expect.objectContaining({
      id,
      name: "Business travel",
      version: 1,
      appliedRevision: 0,
      active: true,
    }),
  ]);
  await page.getByRole("link", { name: "View policy", exact: true }).click();
  await page.getByRole("button", { name: "View: Revision 0", exact: true }).click();
  await expect(page.getByRole("region", { name: "Revision 0", exact: true })).toContainText(
    "Travel reimbursement policy",
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-expense-real-history.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("link", { name: "Back to policies", exact: true }).click();
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("false");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("table", { name: "Expense policies", exact: true })).toContainText(
    "Scheduled travel archive",
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-expense-real-catalog.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "View: Scheduled travel archive", exact: true }).click();
  await page.getByRole("link", { name: "Edit policy", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Nama", { exact: true })).toHaveValue("Scheduled travel archive");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-expense-real-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
