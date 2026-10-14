import { randomUUID } from "node:crypto";
import { type Browser, type BrowserContext, expect, type Page } from "@playwright/test";
import { signInAndEnrollAuthenticator } from "../../integration/fixtures/sign-in-and-enroll-authenticator";
import { payrollCalculationMinor } from "../../src/features/payroll/data/mappers/payroll-run-employee-mapper";
import {
  payrollReviewDetailsDto,
  payrollReviewPreparationDto,
} from "../../src/features/payroll/data/models/payroll-review-dto";

/** Review the actual worker output using independently authenticated company actors. */
export async function exercisePayrollReview(input: {
  browser: Browser;
  baseURL: string;
  admin: BrowserContext;
  preparer: BrowserContext;
  reviewer: BrowserContext;
  editor: Page;
  checker: Page;
  company: string;
  run: string;
  month: string;
  firstReviewer: string;
  headers: () => Record<string, string>;
}) {
  const {
      browser,
      baseURL,
      admin,
      preparer,
      reviewer,
      editor,
      checker,
      company,
      run,
      month,
      firstReviewer,
      headers,
    } = input,
    base = `/api/v1/companies/${company}`,
    secondReviewer = "a0000000-0000-4000-8000-000000000053";
  const member = await admin.request.put(`${base}/members/${secondReviewer}`, {
    headers: headers(),
    data: {
      active: true,
      permissions: ["company.read", "payroll.review"],
      reason: "Independent second payroll stage",
    },
  });
  expect(member.status()).toBe(200);
  const template = await admin.request.put(`${base}/approvals/templates/${randomUUID()}`, {
    headers: headers(),
    data: {
      name: "Independent payroll review",
      kind: "PAYROLL",
      active: true,
      expectedVersion: null,
      effectiveFrom: `${month}-01`,
      minimumAmount: "0",
      stages: [
        { assignment: "NAMED", accountIds: [firstReviewer] },
        { assignment: "NAMED", accountIds: [secondReviewer] },
      ],
      reason: "Review payroll with two independent stages",
    },
  });
  expect(template.status()).toBe(200);
  expect((await admin.request.get(`${base}/payroll/runs/${run}/review-preparation`)).status()).toBe(
    403,
  );
  const finalReviewer = await browser.newContext({ baseURL });
  try {
    const second = await finalReviewer.newPage();
    await signInAndEnrollAuthenticator(second, "browser-payroll-final-reviewer@example.invalid");
    await second.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
    await editor.goto(`/payroll/runs/${run}?company=${company}`);
    await editor.getByRole("link", { name: "Payroll reviews", exact: true }).click();
    await expect(
      editor.getByRole("region", { name: "Retained totals", exact: true }),
    ).toBeVisible();
    const preparation = payrollReviewPreparationDto.parse(
      await (await preparer.request.get(`${base}/payroll/runs/${run}/review-preparation`)).json(),
    );
    expect(preparation.totals.employeeCount).toBe(1);
    expect(preparation.availableActions).toEqual(["SUBMIT"]);
    const writes: { operation: string; body: Record<string, unknown> }[] = [];
    let drop = true;
    await editor.route(`**${base}/payroll/runs/${run}/reviews`, async (route) => {
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
    await editor.getByRole("link", { name: "Submit for review", exact: true }).click();
    await editor.getByLabel("Reason", { exact: true }).fill("Review retained payroll totals");
    await editor.getByRole("button", { name: "Submit for review", exact: true }).click();
    await editor.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Payroll submitted for review.");
    expect(writes).toHaveLength(2);
    expect(writes[1]).toEqual(writes[0]);
    const id = String(writes[0]?.body.id),
      path = `${base}/payroll/reviews/${id}`,
      route = `/payroll/reviews/${id}?company=${company}`;
    await editor
      .getByRole("status")
      .getByRole("link", { name: "View review", exact: true })
      .click();
    await expect(editor.getByRole("link", { name: "Approve", exact: true })).toHaveCount(0);
    const submitted = payrollReviewDetailsDto.parse(
      await (await preparer.request.get(path)).json(),
    );
    expect(submitted.review.status).toBe("PENDING");
    expect(submitted.approval.stages).toEqual([[firstReviewer], [secondReviewer]]);
    for (const key of ["taxableGross", "withheld", "takeHome"] as const)
      expect(payrollCalculationMinor(submitted.review.totals[key])).toBe(
        payrollCalculationMinor(preparation.totals[key]),
      );
    await checker.goto(`/approvals/${submitted.approval.id}?company=${company}`);
    await checker.getByRole("link", { name: "View source request", exact: true }).click();
    await expect(checker).toHaveURL(new RegExp(`/payroll/reviews/${id}\\?company=`));
    await checker.getByRole("link", { name: "Approve", exact: true }).click();
    await checker.getByRole("button", { name: "Approve", exact: true }).click();
    await expect(checker.getByRole("status")).toContainText("Decision recorded.");
    const first = payrollReviewDetailsDto.parse(await (await reviewer.request.get(path)).json());
    expect(first.review.status).toBe("PENDING");
    expect(first.approval.currentStep).toBe(1);
    expect(first.availableActions).toEqual([]);
    const decisions: { operation: string; body: Record<string, unknown> }[] = [];
    let dropDecision = true;
    await second.route(`**${path}/decisions`, async (route) => {
      decisions.push({
        operation: route.request().headers()["idempotency-key"] ?? "",
        body: route.request().postDataJSON() as Record<string, unknown>,
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (dropDecision) {
        dropDecision = false;
        return route.abort("failed");
      }
      return route.fulfill({ response });
    });
    await second.goto(route);
    await second.getByRole("link", { name: "Approve", exact: true }).click();
    await second
      .getByLabel("Reason (optional)", { exact: true })
      .fill("Independent final stage review");
    await second.getByRole("button", { name: "Approve", exact: true }).click();
    await second.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect(second.getByRole("status")).toContainText("Decision recorded.");
    expect(decisions).toHaveLength(2);
    expect(decisions[1]).toEqual(decisions[0]);
    await second
      .getByRole("status")
      .getByRole("link", { name: "View review", exact: true })
      .click();
    await expect(second.getByRole("region", { name: "Overview", exact: true })).toContainText(
      "Approved",
    );
    await second.getByRole("tab", { name: "History", exact: true }).click();
    await expect(
      second.getByRole("table", { name: "History", exact: true }).getByRole("row"),
    ).toHaveCount(4);
    await expect(
      second
        .getByRole("table", { name: "History", exact: true })
        .getByRole("row")
        .nth(2)
        .getByRole("cell")
        .nth(4),
    ).toHaveText("—");
    await second.screenshot({
      path: "../../.work/dashboard-payroll-review-real-approved.png",
      fullPage: true,
      animations: "disabled",
    });
    await editor.goto(route);
    await editor.getByRole("link", { name: "Withdraw", exact: true }).click();
    await editor.getByLabel("Reason", { exact: true }).fill("Review the retained reference again");
    await editor.getByRole("button", { name: "Withdraw", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Review withdrawn.");
    await editor
      .getByRole("status")
      .getByRole("link", { name: "View review", exact: true })
      .click();
    await editor.getByRole("link", { name: "All reviews", exact: true }).click();
    await editor.getByRole("link", { name: "Submit for review", exact: true }).click();
    await editor.getByLabel("Reason", { exact: true }).fill("Submit a second independent review");
    await editor.getByRole("button", { name: "Submit for review", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Payroll submitted for review.");
    expect(writes).toHaveLength(3);
    const secondId = String(writes[2]?.body.id);
    expect(secondId).not.toBe(id);
    await checker.goto(`/payroll/reviews/${secondId}?company=${company}`);
    await checker.getByRole("link", { name: "Reject", exact: true }).click();
    await checker
      .getByLabel("Reason", { exact: true })
      .fill("Recalculate after a further source review");
    await checker.getByRole("button", { name: "Reject", exact: true }).click();
    await expect(checker.getByRole("status")).toContainText("Decision recorded.");
    await editor.goto(`/payroll/runs/${run}/reviews?company=${company}`);
    await expect(editor.getByRole("alert")).toContainText("Start a new calculation");
    await expect(
      editor.getByRole("table", { name: "History", exact: true }).getByRole("row"),
    ).toHaveCount(3);
    await expect(editor.getByRole("link", { name: "Submit for review", exact: true })).toHaveCount(
      0,
    );
    const retained = payrollReviewDetailsDto.parse(await (await preparer.request.get(path)).json());
    expect(retained.review.status).toBe("WITHDRAWN");
    expect(retained.review.totals).toEqual(submitted.review.totals);
    expect(retained.changes).toHaveLength(4);
  } finally {
    await finalReviewer.close();
  }
}
