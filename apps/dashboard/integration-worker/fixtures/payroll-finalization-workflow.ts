import { randomUUID } from "node:crypto";
import { type Browser, type BrowserContext, expect, type Page } from "@playwright/test";
import { signInAndEnrollAuthenticator } from "../../integration/fixtures/sign-in-and-enroll-authenticator";
import { payrollCalculationMinor } from "../../src/features/payroll/data/mappers/payroll-run-employee-mapper";
import { payrollRunDetailsDto } from "../../src/features/payroll/data/models/payroll-calculation-dto";
import {
  payrollFinalizationDetailsDto,
  payrollFinalizationPreparationDto,
} from "../../src/features/payroll/data/models/payroll-finalization-dto";
import { payrollPayslipPageDto } from "../../src/features/payroll/data/models/payroll-payslip-dto";
import { payrollReviewPreparationDto } from "../../src/features/payroll/data/models/payroll-review-dto";
import { exercisePayrollPayslip } from "./payroll-payslip-workflow";

/** Publish a fresh, independently approved run through the real worker and review its retained output. */
export async function exercisePayrollFinalization(input: {
  browser: Browser;
  baseURL: string;
  admin: BrowserContext;
  preparer: BrowserContext;
  finalReviewer: BrowserContext;
  editor: Page;
  checker: Page;
  second: Page;
  company: string;
  rejectedRun: string;
  period: string;
  employee: string;
  month: string;
  incomeDueDate: string;
  headers: () => Record<string, string>;
}) {
  const {
      browser,
      baseURL,
      admin,
      preparer,
      finalReviewer,
      editor,
      checker,
      second,
      company,
      rejectedRun,
      period,
      employee,
      month,
      incomeDueDate,
      headers,
    } = input,
    base = `/api/v1/companies/${company}`,
    finalizerId = "a0000000-0000-4000-8000-000000000054";
  const membership = await admin.request.put(`${base}/members/${finalizerId}`, {
    headers: headers(),
    data: {
      active: true,
      permissions: ["company.read", "payroll.finalize"],
      reason: "Independent payroll publication acceptance",
    },
  });
  expect(membership.status()).toBe(200);
  await editor.goto(`/payroll/runs/${rejectedRun}/abandon?company=${company}`);
  await editor.getByLabel("Reason", { exact: true }).fill("Recalculate after rejected review");
  await editor.getByRole("button", { name: "Abandon run", exact: true }).click();
  await expect(editor.getByRole("status")).toContainText("Run abandoned.");
  await editor.goto(`/payroll/periods/${period}/calculate?company=${company}`);
  await editor.getByLabel("Income due date", { exact: true }).fill(incomeDueDate);
  await editor
    .getByLabel("Review reference", { exact: true })
    .fill("Final reviewed payroll liabilities");
  await editor.getByLabel("Reason", { exact: true }).fill("Calculate payroll for publication");
  await editor.getByRole("button", { name: "Start calculation", exact: true }).click();
  await expect(editor.getByRole("status")).toContainText("Calculation queued.");
  const runLink = editor.getByRole("link", { name: "View run", exact: true });
  await expect(runLink).toHaveAttribute("href", /\/payroll\/runs\/[0-9a-f-]+\?company=/u);
  const target = await runLink.getAttribute("href");
  if (!target) throw new Error("Acknowledged calculation navigation required");
  const run = new URL(target, baseURL).pathname.split("/").at(-1);
  if (!run) throw new Error("Acknowledged run identifier required");
  const runPath = `${base}/payroll/runs/${run}`;
  await runLink.click();
  await expect
    .poll(
      async () => {
        const response = await preparer.request.get(`${runPath}?limit=50`);
        expect(response.status()).toBe(200);
        return payrollRunDetailsDto.parse(await response.json()).run.status;
      },
      { timeout: 30_000, intervals: [250, 500, 1000] },
    )
    .toBe("CALCULATED");
  const reviewPreparation = payrollReviewPreparationDto.parse(
    await (await preparer.request.get(`${runPath}/review-preparation`)).json(),
  );
  expect(reviewPreparation.availableActions).toEqual(["SUBMIT"]);
  const csrf = (await (await preparer.request.get("/api/v1/auth/csrf")).json()) as {
      headerName: string;
      token: string;
    },
    review = randomUUID();
  const submitted = await preparer.request.post(`${runPath}/reviews`, {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: {
      id: review,
      expectedRunVersion: reviewPreparation.run.version,
      reason: "Review publication-ready calculation",
    },
  });
  expect(submitted.status()).toBe(200);
  for (const approver of [checker, second]) {
    await approver.goto(`/payroll/reviews/${review}?company=${company}`);
    await approver.getByRole("link", { name: "Approve", exact: true }).click();
    await approver.getByRole("button", { name: "Approve", exact: true }).click();
    await expect(approver.getByRole("status")).toContainText("Decision recorded.");
  }
  const publisher = await browser.newContext({ baseURL });
  try {
    const page = await publisher.newPage();
    await signInAndEnrollAuthenticator(page, "browser-payroll-finalizer@example.invalid");
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
    for (const path of [
      runPath,
      `${base}/payroll/reviews/${review}`,
      `${base}/payroll/payslips?from=${month}&until=${month}`,
    ])
      expect((await publisher.request.get(path)).status()).toBe(403);
    await page.goto(`/payroll/publications?company=${company}&from=${month}&until=${month}`);
    await page
      .getByRole("button", {
        name: new RegExp(`^Review finalization: Calculation 3 · ${month}$`, "u"),
      })
      .click();
    await expect(page.getByRole("region", { name: "Reviewed totals", exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: /^(Calculation|Approval review)$/u })).toHaveCount(
      0,
    );
    const preparation = payrollFinalizationPreparationDto.parse(
      await (await publisher.request.get(`${runPath}/finalization-preparation`)).json(),
    );
    expect(preparation.availableActions).toEqual(["START"]);
    expect(preparation.latestReview?.id).toBe(review);
    const writes: { operation: string; body: Record<string, unknown> }[] = [];
    let drop = true;
    await page.route(`**${runPath}/finalizations`, async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      writes.push({
        operation: route.request().headers()["idempotency-key"] ?? "",
        body: route.request().postDataJSON() as Record<string, unknown>,
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (drop) {
        drop = false;
        return route.abort("failed");
      }
      return route.fulfill({ response });
    });
    await page.getByRole("link", { name: "Review publication", exact: true }).click();
    await page.getByLabel("Reason", { exact: true }).fill("Publish independently approved payroll");
    await page.getByRole("button", { name: "Publish payroll", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Retry original command", exact: true }),
    ).toBeVisible();
    const id = String(writes[0]?.body.id),
      path = `${base}/payroll/finalizations/${id}`;
    await expect
      .poll(
        async () => {
          const response = await publisher.request.get(path);
          expect(response.status()).toBe(200);
          return payrollFinalizationDetailsDto.parse(await response.json()).job.status;
        },
        { timeout: 30_000, intervals: [250, 500, 1000] },
      )
      .toBe("SUCCEEDED");
    // The original acknowledgement must remain recoverable even after the worker changes the run.
    await page.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Publication requested.");
    expect(writes).toHaveLength(2);
    expect(writes[1]).toEqual(writes[0]);
    expect(writes[0]?.body).toMatchObject({
      reviewId: review,
      expectedRunVersion: preparation.run.version,
      expectedReviewVersion: preparation.latestReview?.version,
      expectedApprovalVersion: preparation.approvalVersion,
    });
    await page.getByRole("link", { name: "View publication", exact: true }).click();
    await expect(page.getByRole("status")).toContainText(
      "Payslips published. Payment is managed separately.",
    );
    const published = payrollFinalizationDetailsDto.parse(
      await (await publisher.request.get(path)).json(),
    );
    expect(published.run).toMatchObject({ id: run, status: "FINALIZED", finalizationId: id });
    expect(published.finalization).toMatchObject({
      id,
      number: 1,
      reviewId: review,
      actorId: finalizerId,
    });
    expect(published.finalization.publishedAt).not.toBeNull();
    expect(published.totals).toEqual(preparation.totals);
    expect(
      (await (await publisher.request.get(`${runPath}/finalizations?limit=8`)).json()).items,
    ).toHaveLength(1);
    const complete = payrollFinalizationPreparationDto.parse(
      await (await publisher.request.get(`${runPath}/finalization-preparation`)).json(),
    );
    expect(complete.availableActions).toEqual([]);
    expect(complete.latestJobStatus).toBe("SUCCEEDED");
    await page.screenshot({
      path: "../../.work/dashboard-payroll-finalization-real-published.png",
      fullPage: true,
      animations: "disabled",
    });
    await page.getByRole("link", { name: "Job details", exact: true }).click();
    await expect(page.getByRole("region", { name: "Job details", exact: true })).toContainText(
      "Succeeded",
    );
    await page.goto(`/payroll/runs/${run}/finalizations?company=${company}`);
    await expect(
      page.getByRole("table", { name: "Publication history", exact: true }).getByRole("row"),
    ).toHaveCount(2);
    await expect(page.getByRole("link", { name: "Review publication", exact: true })).toHaveCount(
      0,
    );
    const readGrant = await admin.request.put(
      `${base}/members/a0000000-0000-4000-8000-000000000053`,
      {
        headers: headers(),
        data: {
          active: true,
          expectedVersion: 0,
          permissions: ["company.read", "payroll.read"],
          reason: "Inspect published payslip with independent read authority",
        },
      },
    );
    expect(readGrant.status()).toBe(200);
    const payslips = await finalReviewer.request.get(
      `${base}/payroll/payslips?from=${month}&until=${month}&employeeId=${employee}`,
    );
    expect(payslips.status()).toBe(200);
    const summaries = payrollPayslipPageDto.parse(await payslips.json()).items;
    expect(summaries).toHaveLength(1);
    const summary = summaries[0];
    if (!summary) throw new Error("Published payslip required");
    expect(summary.employeeId).toBe(employee);
    for (const key of ["taxableGross", "withheld", "takeHome"] as const)
      expect(payrollCalculationMinor(summary[key])).toBe(
        payrollCalculationMinor(preparation.totals[key]),
      );
    const pdf = await finalReviewer.request.get(
      `${base}/payroll/payslips/${summary.id}/pdf?language=EN`,
    );
    expect(pdf.status()).toBe(200);
    expect(pdf.headers()["content-type"]).toContain("application/pdf");
    expect((await pdf.body()).subarray(0, 5).toString()).toBe("%PDF-");
    expect((await publisher.request.get(`${base}/payroll/payslips/${summary.id}`)).status()).toBe(
      403,
    );
    await exercisePayrollPayslip({ reader: second, admin, company, month, summary, headers });
    const revoked = await admin.request.put(`${base}/members/${finalizerId}`, {
      headers: headers(),
      data: {
        active: true,
        expectedVersion: 0,
        permissions: ["company.read"],
        reason: "Withdraw publication authority",
      },
    });
    expect(revoked.status()).toBe(200);
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page.getByRole("region", { name: "Reviewed totals", exact: true })).toHaveCount(0);
  } finally {
    await publisher.close();
  }
}
