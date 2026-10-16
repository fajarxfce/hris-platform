import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "../integration/fixtures/sign-in-and-enroll-authenticator";
import type { PayrollInputReviewDto } from "../src/features/payroll/data/models/payroll-input-dto";
import type { WorkPeriodOverviewDto } from "../src/features/workforce/data/models/work-period-overview-dto";

test("monthly input preparation uses closed worker evidence and independent versioned verification", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  test.setTimeout(120_000);
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-payroll-input-admin@example.invalid",
    "PAY_INPUT",
    "Payroll Input Company",
  );
  const base = `/api/v1/companies/${company}`,
    employee = randomUUID(),
    preparerId = "a0000000-0000-4000-8000-000000000045",
    reviewerId = "a0000000-0000-4000-8000-000000000046";
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  for (const [account, permissions] of [
    [preparerId, ["company.read", "payroll.calculate", "payroll.review"]],
    [reviewerId, ["company.read", "payroll.review"]],
  ] as const) {
    const response = await context.request.put(`${base}/members/${account}`, {
      headers: headers(),
      data: { active: true, permissions, reason: "Independent monthly payroll input roles" },
    });
    expect(response.status()).toBe(200);
  }
  const employment = await context.request.post(`${base}/employees`, {
    headers: headers(),
    data: {
      id: employee,
      employeeNumber: "INPUT-001",
      person: { id: randomUUID(), legalName: "Monthly Payroll Employee", nationality: "ID" },
      terms: {
        effectiveFrom: "2025-01-01",
        startDate: "2025-01-01",
        status: "ACTIVE",
        contract: "PERMANENT",
      },
      reason: "Payroll input acceptance",
    },
  });
  expect(employment.status()).toBe(200);
  const calendar = await context.request.get(`${base}/workforce/periods/2026-01`);
  expect(calendar.status()).toBe(200);
  const companyDate = ((await calendar.json()) as WorkPeriodOverviewDto).companyDate;
  const month = new Date(Date.parse(`${companyDate.slice(0, 7)}-01T00:00:00Z`) - 86_400_000)
      .toISOString()
      .slice(0, 7),
    input = `${base}/payroll/employees/${employee}/inputs/${month}`;
  expect((await context.request.get(`${input}/review`)).status()).toBe(403);
  if (!baseURL) throw new Error("Integration base URL required");
  const preparer = await browser.newContext({ baseURL }),
    reviewer = await browser.newContext({ baseURL });
  try {
    const editor = await preparer.newPage();
    await signInAndEnrollAuthenticator(editor, "browser-payroll-input-preparer@example.invalid");
    await editor.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
    await editor.goto(`/payroll/inputs/${employee}?company=${company}&month=${month}`);
    await expect(
      editor.getByRole("status").filter({ hasText: "Close attendance for this month" }),
    ).toBeVisible();
    await expect(editor.getByRole("link", { name: "Prepare input", exact: true })).toHaveCount(0);
    const before = (await (
      await context.request.get(`${base}/workforce/periods/${month}`)
    ).json()) as WorkPeriodOverviewDto;
    const closing = await context.request.post(`${base}/workforce/periods/${month}/close`, {
      headers: headers(),
      data: {
        expectedVersion: before.period?.version ?? 0,
        reason: "Closed attendance for monthly payroll input",
      },
    });
    expect(closing.status()).toBe(200);
    await expect
      .poll(
        async () => {
          const response = await context.request.get(`${base}/workforce/periods/${month}`);
          expect(response.status()).toBe(200);
          return ((await response.json()) as WorkPeriodOverviewDto).period?.status;
        },
        { timeout: 30_000, intervals: [250, 500, 1000] },
      )
      .toBe("CLOSED");
    await editor.getByRole("button", { name: "Refresh", exact: true }).click();
    await editor.getByRole("link", { name: "Prepare input", exact: true }).click();
    await editor.getByLabel("Taxable non-cash income (IDR)", { exact: true }).fill("0");
    await editor.getByRole("button", { name: "Add earning", exact: true }).click();
    const earning = editor.getByRole("group", { name: "Variable earnings 1", exact: true });
    await earning.getByLabel("Code", { exact: true }).fill("BONUS");
    await earning.getByLabel("Name", { exact: true }).fill("Monthly bonus");
    await earning.getByLabel("Amount (IDR)", { exact: true }).fill("2000000.25");
    await editor.getByLabel("Review reference", { exact: true }).fill("Reviewed closed attendance");
    await editor.getByLabel("Reason", { exact: true }).fill("Prepare monthly payroll input");
    const writes: { operation: string; body: Record<string, unknown> }[] = [];
    let dropSave = true;
    await editor.route(`**${input}`, async (route) => {
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
    await expect(editor.getByRole("status")).toContainText("Input saved.");
    expect(writes).toHaveLength(2);
    expect(writes[1]).toEqual(writes[0]);
    expect(writes[0]?.body).not.toHaveProperty("workAdjustmentId");
    await editor.getByRole("link", { name: "View input", exact: true }).click();
    await expect(editor.getByRole("link", { name: "Verify input", exact: true })).toHaveCount(0);
    const stored = await preparer.request.get(`${input}/review`);
    expect(stored.status()).toBe(200);
    const original = (await stored.json()) as PayrollInputReviewDto;
    expect(original.current?.version).toBe(0);
    expect(original.current?.workJobId).toBe(original.source?.jobId);
    expect(original.history.items).toHaveLength(1);
    expect((await preparer.request.get(`${base}/employees?asOf=${companyDate}`)).status()).toBe(
      403,
    );
    const operatorCsrf = (await (await preparer.request.get("/api/v1/auth/csrf")).json()) as {
      headerName: string;
      token: string;
    };
    const operatorHeaders = () => ({
      [operatorCsrf.headerName]: operatorCsrf.token,
      "Idempotency-Key": randomUUID(),
    });
    const selfReview = await preparer.request.post(`${input}/verify`, {
      headers: operatorHeaders(),
      data: { expectedVersion: 0, reason: "Author cannot verify" },
    });
    expect(selfReview.status()).toBe(403);
    expect(await selfReview.json()).toMatchObject({ code: "payroll_input_author_cannot_verify" });
    const checker = await reviewer.newPage();
    await signInAndEnrollAuthenticator(checker, "browser-payroll-input-reviewer@example.invalid");
    await checker.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
    await checker.goto(`/payroll/inputs/${employee}/verify?company=${company}&month=${month}`);
    await expect(
      checker.getByRole("heading", { name: "Verify monthly input", exact: true }),
    ).toBeVisible();
    await checker.getByLabel("Reason", { exact: true }).fill("Review first draft");
    if (!original.current || !original.source?.jobId)
      throw new Error("Expected an input backed by closed attendance");
    const changed = await preparer.request.put(input, {
      headers: operatorHeaders(),
      data: {
        expectedVersion: 0,
        expectedEmploymentVersion: original.employee.employmentVersion,
        workJobId: original.source.jobId,
        expectedWorkPeriodVersion: original.source.version,
        terms: {
          ...original.current.terms,
          earnings: [{ code: "BONUS", name: "Revised bonus", amount: "2500000.50", taxable: true }],
        },
        reason: "Revise the reviewed earning",
      },
    });
    expect(changed.status()).toBe(200);
    await checker.getByRole("button", { name: "Verify input", exact: true }).click();
    await expect(checker.getByRole("button", { name: "Reload review", exact: true })).toBeVisible();
    await checker.getByRole("button", { name: "Reload review", exact: true }).click();
    await checker
      .getByRole("dialog")
      .getByRole("button", { name: "Leave page", exact: true })
      .click();
    await expect(
      checker.getByRole("table", { name: "Variable earnings", exact: true }),
    ).toContainText("Revised bonus");
    await checker.evaluate(() => window.scrollTo(0, 0));
    await checker.screenshot({
      path: "../../.work/dashboard-payroll-input-real-review.png",
      fullPage: true,
      animations: "disabled",
    });
    await checker.getByLabel("Reason", { exact: true }).fill("Independent review of revised input");
    let dropVerify = true;
    const verifications: { operation: string; body: Record<string, unknown> }[] = [];
    await checker.route(`**${input}/verify`, async (route) => {
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
    await checker.getByRole("button", { name: "Verify input", exact: true }).click();
    await expect(checker.getByRole("status")).toContainText("The result is unconfirmed.");
    await checker.getByRole("button", { name: "Retry request", exact: true }).click();
    await expect(checker.getByRole("status")).toContainText("Input verified.");
    expect(verifications).toHaveLength(2);
    expect(verifications[1]).toEqual(verifications[0]);
    expect(verifications[0]?.body.expectedVersion).toBe(1);
    await checker.getByRole("link", { name: "View input", exact: true }).click();
    await checker.getByRole("tab", { name: "History", exact: true }).click();
    await expect(
      checker.getByRole("table", { name: "History", exact: true }).getByRole("row"),
    ).toHaveCount(4);
    await checker.getByRole("button", { name: "View revision: Revision 0", exact: true }).click();
    await expect(
      checker.getByRole("dialog").getByRole("table", { name: "Variable earnings", exact: true }),
    ).toContainText("Monthly bonus");
    await checker.getByRole("button", { name: "Close", exact: true }).click();
    await checker.setViewportSize({ width: 390, height: 844 });
    await checker.getByRole("button", { name: "Theme", exact: true }).click();
    expect(
      await checker.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await checker.evaluate(() => window.scrollTo(0, 0));
    await checker.screenshot({
      path: "../../.work/dashboard-payroll-input-real-history-narrow.png",
      fullPage: true,
      animations: "disabled",
    });

    const adjustments = `${base}/workforce/employees/${employee}/attendance/adjustments`,
      workDate = `${month}-02`;
    const adjustAttendance = async (absence: boolean) => {
      const review = await context.request.get(`${adjustments}/review?workDate=${workDate}`);
      expect(review.status()).toBe(200);
      const observed = (await review.json()) as { revision: string };
      const response = await context.request.post(adjustments, {
        headers: headers(),
        data: {
          workDate,
          reviewRevision: observed.revision,
          clockIn: absence ? null : `${workDate}T01:00:00Z`,
          clockOut: absence ? null : `${workDate}T09:00:00Z`,
          breakMinutes: absence ? 0 : 60,
          decisions: [],
          reason: absence ? "Reviewed absence after closing" : "Reviewed worked day after closing",
        },
      });
      expect(response.status()).toBe(200);
      return (await response.json()) as { id: string; version: number };
    };
    const firstAdjustment = await adjustAttendance(false);
    expect(firstAdjustment.version).toBe(1);
    await editor.goto(`/payroll/inputs/${employee}/edit?company=${company}&month=${month}`);
    await expect(
      editor.getByRole("region", { name: "Attendance source", exact: true }),
    ).toContainText("Attendance revision 1");
    await editor
      .getByLabel("Reason", { exact: true })
      .fill("Prepare input using corrected attendance");
    dropSave = true;
    await editor.getByRole("button", { name: "Save draft", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("The result is unconfirmed.");
    const latestAdjustment = await adjustAttendance(true);
    expect(latestAdjustment.version).toBe(2);
    await editor.getByRole("button", { name: "Retry request", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Input saved.");
    expect(writes).toHaveLength(4);
    expect(writes[2]?.body.workAdjustmentId).toBe(firstAdjustment.id);
    expect(writes[3]).toEqual(writes[2]);
    await editor.getByRole("link", { name: "View input", exact: true }).click();
    await expect(editor.getByRole("region", { name: "Revision 3", exact: true })).toContainText(
      firstAdjustment.id,
    );
    await expect(
      editor.getByRole("region", { name: "Attendance source", exact: true }),
    ).toContainText("Attendance revision 2");
    await checker.goto(`/payroll/inputs/${employee}?company=${company}&month=${month}`);
    await expect(
      checker.getByText(
        "The attendance or employment version has changed. Prepare a new input before verification.",
        { exact: true },
      ),
    ).toBeVisible();
    await expect(checker.getByRole("link", { name: "Verify input", exact: true })).toHaveCount(0);
    await editor.getByRole("link", { name: "Edit input", exact: true }).click();
    await editor.getByLabel("Reason", { exact: true }).fill("Review the latest attendance source");
    await editor.getByRole("button", { name: "Save draft", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Input saved.");
    expect(writes[4]?.body.workAdjustmentId).toBe(latestAdjustment.id);
    expect(writes[4]?.operation).not.toBe(writes[2]?.operation);
    await checker.goto(`/payroll/inputs/${employee}/verify?company=${company}&month=${month}`);
    await expect(
      checker.getByRole("region", { name: "Attendance source", exact: true }),
    ).toContainText("Reviewed absence after closing");
    await checker
      .getByLabel("Reason", { exact: true })
      .fill("Independently verify corrected attendance source");
    dropVerify = true;
    await checker.getByRole("button", { name: "Verify input", exact: true }).click();
    await expect(checker.getByRole("status")).toContainText("The result is unconfirmed.");
    await checker.getByRole("button", { name: "Retry request", exact: true }).click();
    await expect(checker.getByRole("status")).toContainText("Input verified.");
    expect(verifications).toHaveLength(4);
    expect(verifications[2]?.body.expectedVersion).toBe(4);
    expect(verifications[3]).toEqual(verifications[2]);
    const adjusted = await preparer.request.get(`${input}/review`);
    expect(adjusted.status()).toBe(200);
    const finalReview = (await adjusted.json()) as PayrollInputReviewDto;
    expect(finalReview.current).toMatchObject({
      version: 5,
      status: "VERIFIED",
      workJobId: original.source.jobId,
      workPeriodVersion: original.source.version,
      workAdjustmentId: latestAdjustment.id,
    });
    expect(finalReview.history.items).toHaveLength(6);
    await checker.getByRole("link", { name: "View input", exact: true }).click();
    await checker.getByRole("tab", { name: "History", exact: true }).click();
    await checker.getByRole("button", { name: "View revision: Revision 0", exact: true }).click();
    await expect(checker.getByRole("dialog")).toContainText("Original closing");
    await checker.getByRole("button", { name: "Close", exact: true }).click();
    await checker.getByRole("tab", { name: "Overview", exact: true }).click();
    await checker.evaluate(() => window.scrollTo(0, 0));
    await checker.screenshot({
      path: "../../.work/dashboard-payroll-attendance-real-source.png",
      fullPage: true,
      animations: "disabled",
    });
    await checker.getByRole("tab", { name: "History", exact: true }).click();
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
