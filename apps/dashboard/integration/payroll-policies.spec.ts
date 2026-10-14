import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { companyDate } from "../src/core/presentation/dates/company-date";
import { createAdminCompany } from "./fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

test("real payroll policy administration preserves dated rules, independent access and recovered saves", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-payroll-policy-admin@example.invalid",
    "PAY_POLICY",
    "Browser Payroll Policies",
  );
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const other = await context.request.post("/api/v1/companies", {
    headers: headers(),
    data: { code: "PAY_OTHER", name: "Other Payroll Company", timezone: "Asia/Jakarta" },
  });
  expect(other.status()).toBe(200);
  const otherCompany = ((await other.json()) as { id: string }).id;
  const account = "a0000000-0000-4000-8000-000000000039";
  for (const id of [company, otherCompany]) {
    const granted = await context.request.put(`/api/v1/companies/${id}/members/${account}`, {
      headers: headers(),
      data: {
        active: true,
        permissions: ["company.read", "payroll.policy.manage"],
        reason: "Independent payroll policy administrator",
      },
    });
    expect(granted.status()).toBe(200);
  }
  const month = companyDate("Asia/Jakarta", new Date()).slice(0, 7),
    year = month.slice(0, 4),
    future = `${Number(year) + 1}-01`,
    nextEnd = `${Number(year) + 1}-12`;
  const endpoint = `/api/v1/companies/${company}/payroll/policy`;
  expect((await context.request.get(`${endpoint}/review?asOf=${month}`)).status()).toBe(403);
  if (!baseURL) throw new Error("Integration base URL is required");
  const operator = await browser.newContext({ baseURL });
  try {
    const editor = await operator.newPage();
    await signInAndEnrollAuthenticator(editor, "browser-payroll-policy-operator@example.invalid");
    await editor.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
    await editor.getByRole("link", { name: "Payroll policy", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("No payroll policy configured.");
    await editor.getByRole("link", { name: "Configure policy", exact: true }).click();
    await expect(
      editor.getByLabel("Pension contribution wage cap (IDR)", { exact: true }),
    ).toHaveValue("");
    await editor.getByLabel("Effective from", { exact: true }).fill(month);
    await editor.getByLabel("Effective through", { exact: true }).fill(`${year}-12`);
    await editor.getByLabel("Minimum monthly wage (IDR)", { exact: true }).fill("4000000.25");
    await editor
      .getByLabel("Health contribution wage cap (IDR)", { exact: true })
      .fill("12000000.75");
    await editor
      .getByLabel("Pension contribution wage cap (IDR)", { exact: true })
      .fill("10000000.50");
    await editor
      .getByLabel("Review references", { exact: true })
      .fill("https://example.test/fictional-payroll-policy");
    await editor.getByLabel("Reason", { exact: true }).fill("Reviewed fictional company policy");
    await editor.evaluate(() => window.scrollTo(0, 0));
    await editor.screenshot({
      path: "../../.work/dashboard-payroll-real-editor.png",
      fullPage: true,
      animations: "disabled",
    });
    await editor.getByRole("button", { name: "Save policy", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Policy saved.");
    await editor.getByRole("link", { name: "View policy", exact: true }).click();
    await expect(
      editor.getByRole("region", { name: "Effective policy", exact: true }),
    ).toContainText("4,000,000.25");
    const noSalaryAccess = await operator.request.get(
      `/api/v1/companies/${company}/payroll/employees/${randomUUID()}/compensation?asOf=${month}`,
    );
    expect(noSalaryAccess.status()).toBe(403);
    const otherReview = await operator.request.get(
      `/api/v1/companies/${otherCompany}/payroll/policy/review?asOf=${month}`,
    );
    expect(otherReview.status()).toBe(200);
    expect(await otherReview.json()).toMatchObject({ current: null, effective: null });
    await editor.getByRole("link", { name: "New revision", exact: true }).click();
    await editor.getByLabel("Effective from", { exact: true }).fill(future);
    await editor.getByLabel("Effective through", { exact: true }).fill(nextEnd);
    await editor.getByLabel("Minimum monthly wage (IDR)", { exact: true }).fill("4100000.75");
    await editor.getByLabel("Reason", { exact: true }).fill("Reviewed following year policy");
    const commands: { operation: string | undefined; body: unknown }[] = [];
    await editor.route(`**${endpoint}`, async (route) => {
      commands.push({
        operation: route.request().headers()["idempotency-key"],
        body: route.request().postDataJSON(),
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (commands.length === 1) return route.abort("failed");
      return route.fulfill({ response });
    });
    await editor.getByRole("button", { name: "Save policy", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("The save result is unconfirmed");
    await editor.getByRole("button", { name: "Retry save", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Policy saved.");
    expect(commands).toHaveLength(2);
    expect(commands[0]).toEqual(commands[1]);
    const review = await operator.request.get(`${endpoint}/review?asOf=${month}`);
    expect(review.status()).toBe(200);
    expect(await review.json()).toMatchObject({
      current: {
        version: 1,
        appliedRevision: 1,
        effectiveFrom: future,
        minimumMonthlyWage: "4100000.75",
      },
      effective: { version: 1, appliedRevision: 0, minimumMonthlyWage: "4000000.25" },
      history: { items: [{ policy: { appliedRevision: 0 } }, { policy: { appliedRevision: 1 } }] },
    });
    await editor.getByRole("link", { name: "View policy", exact: true }).click();
    await editor.getByRole("button", { name: "View: Revision 0", exact: true }).click();
    await expect(editor.getByRole("dialog")).toContainText("Reviewed fictional company policy");
    await editor.screenshot({
      path: "../../.work/dashboard-payroll-real-history.png",
      fullPage: false,
      animations: "disabled",
    });
    await editor.getByRole("button", { name: "Close details", exact: true }).click();
    const revoked = await context.request.put(`/api/v1/companies/${company}/members/${account}`, {
      headers: headers(),
      data: {
        active: true,
        permissions: ["company.read"],
        expectedVersion: 0,
        reason: "Remove payroll administration for isolation acceptance",
      },
    });
    expect(revoked.status()).toBe(200);
    await editor.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(editor.getByRole("alert")).toBeVisible();
    await expect(editor.getByRole("region", { name: "Effective policy", exact: true })).toHaveCount(
      0,
    );
    await expect(editor.getByRole("table", { name: "Policy history", exact: true })).toHaveCount(0);
  } finally {
    await operator.close();
  }
});
