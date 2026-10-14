import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import type { TaxOpeningReviewDto } from "../src/features/payroll/data/models/tax-opening-dto";
import { createAdminCompany } from "./fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

test("opening history preserves annual declarations and requires an independent versioned review", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  test.setTimeout(120_000);
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-tax-opening-admin@example.invalid",
    "TAX_OPEN",
    "Tax Opening Company",
  );
  const base = `/api/v1/companies/${company}`,
    employee = randomUUID(),
    preparerId = "a0000000-0000-4000-8000-000000000048",
    reviewerId = "a0000000-0000-4000-8000-000000000049",
    year = "2025",
    opening = `${base}/payroll/employees/${employee}/tax-openings/${year}`;
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  for (const [account, permissions] of [
    [preparerId, ["company.read", "payroll.calculate", "payroll.review"]],
    [reviewerId, ["company.read", "payroll.review"]],
  ] as const) {
    const response = await context.request.put(`${base}/members/${account}`, {
      headers: headers(),
      data: { active: true, permissions, reason: "Independent opening tax-history roles" },
    });
    expect(response.status()).toBe(200);
  }
  const employment = await context.request.post(`${base}/employees`, {
    headers: headers(),
    data: {
      id: employee,
      employeeNumber: "TAX-001",
      person: { id: randomUUID(), legalName: "Tax History Employee", nationality: "ID" },
      terms: {
        effectiveFrom: "2025-01-01",
        startDate: "2025-01-01",
        status: "ACTIVE",
        contract: "PERMANENT",
      },
      reason: "Opening history acceptance",
    },
  });
  expect(employment.status()).toBe(200);
  expect((await context.request.get(`${opening}/review`)).status()).toBe(403);
  if (!baseURL) throw new Error("Integration base URL required");
  const preparer = await browser.newContext({ baseURL }),
    reviewer = await browser.newContext({ baseURL });
  try {
    const editor = await preparer.newPage();
    await signInAndEnrollAuthenticator(editor, "browser-tax-opening-preparer@example.invalid");
    await editor.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
    await editor.goto(`/payroll/tax-openings?company=${company}&year=${year}`);
    await editor
      .getByRole("button", { name: "Open history: Tax History Employee", exact: true })
      .click();
    await expect(editor.getByRole("status")).toContainText("No opening history has been prepared.");
    await editor.getByRole("link", { name: "Prepare opening", exact: true }).click();
    for (const label of [
      "History through",
      "Tax residency",
      "PTKP status",
      "Taxable gross income (IDR)",
      "Employment months",
    ])
      await expect(editor.getByLabel(label, { exact: true })).toHaveValue("");
    await editor.getByLabel("History through", { exact: true }).selectOption("6");
    await editor.getByLabel("Tax residency", { exact: true }).selectOption("RESIDENT");
    await editor.getByLabel("PTKP status", { exact: true }).selectOption("K0");
    for (const [label, value] of [
      ["Taxable gross income (IDR)", "60000000000.25"],
      ["Retirement contributions (IDR)", "600000.25"],
      ["Qualified donations (IDR)", "0"],
      ["Tax withheld (IDR)", "1500000"],
      ["Previous employer net income (IDR)", "10000000.50"],
      ["Previous employer tax withheld (IDR)", "50000.25"],
      ["Employment months", "6"],
      ["Review reference", "Reviewed June opening history"],
      ["Reason", "Prepare reviewed annual totals"],
    ] as const)
      await editor.getByLabel(label, { exact: true }).fill(value);
    const writes: { operation: string; body: Record<string, unknown> }[] = [];
    let dropSave = true;
    await editor.route(`**${opening}`, async (route) => {
      if (route.request().method() !== "PUT") return route.continue();
      writes.push({
        operation: route.request().headers()["idempotency-key"] ?? "",
        body: route.request().postDataJSON() as Record<string, unknown>,
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (dropSave) {
        dropSave = false;
        return route.abort("failed");
      }
      return route.fulfill({ response });
    });
    await editor.getByRole("button", { name: "Save draft", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("The result is unconfirmed.");
    await editor.getByRole("button", { name: "Retry request", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Opening saved.");
    expect(writes).toHaveLength(2);
    expect(writes[1]).toEqual(writes[0]);
    await editor.getByRole("link", { name: "View history", exact: true }).click();
    await expect(
      editor.getByRole("region", { name: "Year-to-date history", exact: true }),
    ).toContainText("60,000,000,000.25");
    await expect(editor.getByRole("link", { name: "Verify opening", exact: true })).toHaveCount(0);
    const stored = await preparer.request.get(`${opening}/review`);
    expect(stored.status()).toBe(200);
    const original = (await stored.json()) as TaxOpeningReviewDto;
    expect(original.current?.version).toBe(0);
    expect(original.current?.terms.history).toMatchObject({
      taxableGross: "60000000000.25",
      previousEmployerNet: "10000000.50",
      previousEmployerWithheld: "50000.25",
      employmentMonths: 6,
    });
    expect(original.history.items).toHaveLength(1);
    expect(original.history.items[0]).not.toHaveProperty("terms");
    expect((await preparer.request.get(`${base}/employees?asOf=2025-06-30`)).status()).toBe(403);
    const operatorCsrf = (await (await preparer.request.get("/api/v1/auth/csrf")).json()) as {
      headerName: string;
      token: string;
    };
    const operatorHeaders = () => ({
      [operatorCsrf.headerName]: operatorCsrf.token,
      "Idempotency-Key": randomUUID(),
    });
    const selfReview = await preparer.request.post(`${opening}/verify`, {
      headers: operatorHeaders(),
      data: { expectedVersion: 0, reason: "Author cannot verify" },
    });
    expect(selfReview.status()).toBe(403);
    expect(await selfReview.json()).toMatchObject({
      code: "payroll_tax_opening_author_cannot_verify",
    });
    const checker = await reviewer.newPage();
    await signInAndEnrollAuthenticator(checker, "browser-tax-opening-reviewer@example.invalid");
    await checker.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
    await checker.goto(`/payroll/tax-openings/${employee}/verify?company=${company}&year=${year}`);
    await expect(
      checker.getByRole("heading", { name: "Verify opening history", exact: true }),
    ).toBeVisible();
    await checker.getByLabel("Reason", { exact: true }).fill("Review first draft");
    if (!original.current) throw new Error("Expected an explicit opening declaration");
    const changed = await preparer.request.put(opening, {
      headers: operatorHeaders(),
      data: {
        expectedVersion: 0,
        expectedEmploymentVersion: original.employee.employmentVersion,
        terms: {
          ...original.current.terms,
          history: { ...original.current.terms.history, taxableGross: "60000000001.50" },
        },
        reason: "Revise reviewed taxable gross",
      },
    });
    expect(changed.status()).toBe(200);
    await checker.getByRole("button", { name: "Verify opening", exact: true }).click();
    await expect(checker.getByRole("button", { name: "Reload review", exact: true })).toBeVisible();
    await checker.getByRole("button", { name: "Reload review", exact: true }).click();
    await checker
      .getByRole("dialog")
      .getByRole("button", { name: "Leave page", exact: true })
      .click();
    await expect(
      checker.getByRole("region", { name: "Year-to-date history", exact: true }),
    ).toContainText("60,000,000,001.5");
    await checker.evaluate(() => window.scrollTo(0, 0));
    await checker.screenshot({
      path: "../../.work/dashboard-tax-opening-real-review.png",
      fullPage: true,
      animations: "disabled",
    });
    await checker
      .getByLabel("Reason", { exact: true })
      .fill("Independent review of revised history");
    let dropVerify = true;
    const verifications: { operation: string; body: Record<string, unknown> }[] = [];
    await checker.route(`**${opening}/verify`, async (route) => {
      verifications.push({
        operation: route.request().headers()["idempotency-key"] ?? "",
        body: route.request().postDataJSON() as Record<string, unknown>,
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (dropVerify) {
        dropVerify = false;
        return route.abort("failed");
      }
      return route.fulfill({ response });
    });
    await checker.getByRole("button", { name: "Verify opening", exact: true }).click();
    await expect(checker.getByRole("status")).toContainText("The result is unconfirmed.");
    await checker.getByRole("button", { name: "Retry request", exact: true }).click();
    await expect(checker.getByRole("status")).toContainText("Opening verified.");
    expect(verifications).toHaveLength(2);
    expect(verifications[1]).toEqual(verifications[0]);
    expect(verifications[0]?.body.expectedVersion).toBe(1);
    await checker.getByRole("link", { name: "View history", exact: true }).click();
    await checker.getByRole("tab", { name: "History", exact: true }).click();
    await expect(
      checker.getByRole("table", { name: "History", exact: true }).getByRole("row"),
    ).toHaveCount(4);
    await checker.getByRole("button", { name: "View revision: Revision 0", exact: true }).click();
    await expect(
      checker
        .getByRole("dialog")
        .getByRole("region", { name: "Year-to-date history", exact: true }),
    ).toContainText("60,000,000,000.25");
    await checker.getByRole("button", { name: "Close", exact: true }).click();
    await checker.setViewportSize({ width: 390, height: 844 });
    await checker.getByRole("button", { name: "Theme", exact: true }).click();
    expect(
      await checker.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await checker.evaluate(() => window.scrollTo(0, 0));
    await checker.screenshot({
      path: "../../.work/dashboard-tax-opening-real-history-narrow.png",
      fullPage: true,
      animations: "disabled",
    });
    const revoke = await context.request.put(`${base}/members/${reviewerId}`, {
      headers: headers(),
      data: {
        active: true,
        permissions: ["company.read"],
        expectedVersion: 0,
        reason: "Revoke payroll review",
      },
    });
    expect(revoke.status()).toBe(200);
    await checker.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(checker.getByRole("alert")).toBeVisible();
    await expect(checker.getByRole("table", { name: "History", exact: true })).toHaveCount(0);
  } finally {
    await preparer.close();
    await reviewer.close();
  }
});
