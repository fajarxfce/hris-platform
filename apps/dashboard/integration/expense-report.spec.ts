import { randomUUID } from "node:crypto";
import { Temporal } from "@js-temporal/polyfill";
import { expect, test } from "@playwright/test";
import { companyDate } from "../src/core/presentation/dates/company-date";
import { createAdminCompany } from "./fixtures/admin-company";
import { authenticatorCode } from "./fixtures/authenticator";

test("real expense reporting clips submitted transactions, retains exact review return context and replaces only resubmitted facts", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  test.setTimeout(120_000);
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-expense-report-admin@example.invalid",
    "EXPENSE_REPORT",
    "Expense Report Browser",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const base = `/api/v1/companies/${company}`;
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const today = companyDate("Asia/Jakarta", new Date());
  const from = Temporal.PlainDate.from(today).subtract({ days: 1 }).toString();
  const outside = Temporal.PlainDate.from(today).subtract({ days: 2 }).toString();
  const employee = randomUUID(),
    category = randomUUID(),
    claim = randomUUID(),
    submission = randomUUID();
  const reviewer = "a0000000-0000-4000-8000-000000000083";
  expect(
    (
      await context.request.put(`${base}/members/${reviewer}`, {
        headers: headers(),
        data: {
          active: true,
          permissions: ["company.read", "reports.read", "expenses.read", "expenses.approve"],
          reason: "Independent report reviewer",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.post(`${base}/employees`, {
        headers: headers(),
        data: {
          id: employee,
          employeeNumber: "REPORT-EXP-1",
          person: { id: randomUUID(), legalName: "Expense Report Employee", nationality: "ID" },
          terms: {
            effectiveFrom: "2025-01-01",
            startDate: "2025-01-01",
            status: "ACTIVE",
            contract: "PERMANENT",
          },
          reason: "Fictional report employee",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.put(`${base}/expenses/categories/${category}`, {
        headers: headers(),
        data: {
          code: "REPORT_TRAVEL",
          name: "Report travel",
          effectiveFrom: "2025-01-01",
          maximumLineAmount: "500000.25",
          maximumClaimAmount: "2000000.75",
          maximumAgeDays: 30,
          allowedContracts: ["PERMANENT"],
          receiptRequired: false,
          costCenterRequired: false,
          active: true,
          expectedVersion: null,
          reason: "Report acceptance",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.put(`${base}/approvals/templates/${randomUUID()}`, {
        headers: headers(),
        data: {
          name: "Expense report review",
          kind: "EXPENSE",
          effectiveFrom: "2025-01-01",
          stages: [{ assignment: "NAMED", accountIds: [reviewer] }],
          reason: "Independent report review",
        },
      })
    ).status(),
  ).toBe(200);
  const draft = {
    employmentId: employee,
    title: "Report transaction dates",
    description: "Original submitted expenses",
    lines: [
      [outside, "100.10"],
      [from, "0.01"],
      [today, "123.45"],
    ].map(([occurredOn, amount]) => ({
      id: randomUUID(),
      categoryId: category,
      occurredOn,
      amount,
      description: "Travel evidence",
      costCenterId: null,
      receiptRevisionIds: [],
    })),
    expectedVersion: null,
    reason: "Prepare report transactions",
  };
  expect(
    (
      await context.request.put(`${base}/expenses/claims/${claim}/draft`, {
        headers: headers(),
        data: draft,
      })
    ).status(),
  ).toBe(200);
  const reportUrl = `${base}/reports/expenses?from=${from}&until=${today}`;
  expect(await (await context.request.get(reportUrl)).json()).toMatchObject({
    totals: { claims: 0, amount: "0.00" },
  });
  expect(
    (
      await context.request.post(`${base}/expenses/claims/${claim}/submit`, {
        headers: headers(),
        data: {
          submissionId: submission,
          expectedVersion: 0,
          reason: "Submit report transactions",
        },
      })
    ).status(),
  ).toBe(200);
  const selection = new URLSearchParams({ company, from, until: today, status: "PENDING" });
  if (!baseURL) throw new Error("Integration base URL is required");
  const reviewContext = await browser.newContext({ baseURL });
  try {
    const reviewPage = await reviewContext.newPage();
    await reviewPage.goto("/");
    await reviewPage
      .getByLabel("Email", { exact: true })
      .fill("browser-expense-report-reviewer@example.invalid");
    await reviewPage.getByLabel("Password", { exact: true }).fill("Browser-fixture-password-123!");
    await reviewPage.getByRole("button", { name: "Sign in", exact: true }).click();
    await reviewPage.getByRole("button", { name: "Set up an authenticator", exact: true }).click();
    const key = reviewPage.locator(".app-enrollment-key code");
    await expect(key).toBeVisible();
    await reviewPage
      .getByLabel("Authenticator code", { exact: true })
      .fill(authenticatorCode((await key.textContent()) ?? ""));
    await reviewPage.getByRole("button", { name: "Verify", exact: true }).click();
    await reviewPage.getByRole("button", { name: "I have saved the codes", exact: true }).click();
    await reviewPage.goto(`/reports/expenses/claims?${selection}`);
    const rows = reviewPage.getByRole("table", { name: "Claims", exact: true });
    await expect(rows).toContainText("123.46");
    await expect(rows).toContainText("223.56");
    await reviewPage
      .getByRole("button", {
        name: "View submission: Expense Report Employee · Report transaction dates · 1",
        exact: true,
      })
      .click();
    expect(new URL(reviewPage.url()).pathname).toBe(`/expenses/submissions/${submission}`);
    await expect(
      reviewPage.getByRole("region", { name: "Expense submission", exact: true }),
    ).toContainText("223.56");
    await reviewPage.getByRole("tab", { name: "Reviews", exact: true }).click();
    await reviewPage.getByRole("link", { name: "Back to report", exact: true }).click();
    expect(new URL(reviewPage.url()).searchParams.toString()).toBe(selection.toString());
    await reviewPage
      .getByRole("button", {
        name: "View submission: Expense Report Employee · Report transaction dates · 1",
        exact: true,
      })
      .click();
    await reviewPage.getByRole("button", { name: "Return for correction", exact: true }).click();
    await reviewPage.getByLabel("Reason", { exact: true }).fill("Correct the travel amount");
    await reviewPage.getByRole("button", { name: "Return expense", exact: true }).click();
    await expect(reviewPage.getByRole("status")).toContainText("Returned for correction.");
    await reviewPage.getByRole("link", { name: "View submission", exact: true }).click();
    await reviewPage.getByRole("tab", { name: "Reviews", exact: true }).click();
    await expect(reviewPage.getByRole("table", { name: "Reviews", exact: true })).toContainText(
      "Returned",
    );
    await reviewPage.getByRole("link", { name: "Back to report", exact: true }).click();
    expect(new URL(reviewPage.url()).searchParams.toString()).toBe(selection.toString());
    await expect(reviewPage.getByRole("status")).toContainText(
      "No submitted transactions match this period and status.",
    );
  } finally {
    await reviewContext.close();
  }
  expect(await (await context.request.get(reportUrl)).json()).toMatchObject({
    totals: { claims: 1, employments: 1, lines: 2, amount: "123.46" },
    statuses: expect.arrayContaining([
      { status: "RETURNED", counts: { claims: 1, employments: 1, lines: 2, amount: "123.46" } },
    ]),
  });
  const edited = {
    ...draft,
    title: "Corrected report claim",
    description: "Replacement draft",
    expectedVersion: 2,
    lines: [{ ...draft.lines[0], id: randomUUID(), occurredOn: today, amount: "200.02" }],
  };
  expect(
    (
      await context.request.put(`${base}/expenses/claims/${claim}/draft`, {
        headers: headers(),
        data: edited,
      })
    ).status(),
  ).toBe(200);
  await page.goto(`/reports/expenses?company=${company}&from=${from}&until=${today}`);
  const amount = page.getByRole("region", { name: "Amount in period", exact: true });
  await expect(amount).toContainText("123.46");
  await page
    .getByRole("button", { name: "View claims: Draft after submission", exact: true })
    .click();
  await expect(page.getByRole("table", { name: "Claims", exact: true })).toContainText(
    "Report transaction dates",
  );
  await expect(page.getByRole("table", { name: "Claims", exact: true })).not.toContainText(
    "Corrected report claim",
  );
  const replacement = randomUUID();
  expect(
    (
      await context.request.post(`${base}/expenses/claims/${claim}/submit`, {
        headers: headers(),
        data: {
          submissionId: replacement,
          expectedVersion: 3,
          reason: "Resubmit corrected transactions",
        },
      })
    ).status(),
  ).toBe(200);
  await page.getByRole("link", { name: "Back to report", exact: true }).click();
  await expect(amount).toContainText("200.02");
  await expect(page.getByRole("region", { name: "Claims", exact: true })).toContainText("1");
  await page.screenshot({
    path: "../../.work/dashboard-expense-report-real.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "View claims: Pending", exact: true }).click();
  await page
    .getByRole("button", {
      name: "View submission: Expense Report Employee · Corrected report claim · 2",
      exact: true,
    })
    .click();
  expect(new URL(page.url()).pathname).toBe(`/expenses/submissions/${replacement}`);
  expect(
    await (await context.request.get(`${base}/expenses/submissions/${submission}`)).json(),
  ).toMatchObject({
    current: false,
    title: "Report transaction dates",
    totalAmount: "223.56",
    number: 1,
  });
  expect(await (await context.request.get(reportUrl)).json()).toMatchObject({
    totals: { claims: 1, employments: 1, lines: 1, amount: "200.02" },
  });
});
