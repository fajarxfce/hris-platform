import { type BrowserContext, expect } from "@playwright/test";
import { z } from "zod";
import {
  payrollAmendmentReviewDetailsDto,
  payrollAmendmentReviewsDto,
} from "../../src/features/payroll/data/models/payroll-amendment-review-dto";

/** Submit, withdraw, resubmit and independently approve the actual retained correction. */
export async function exercisePayrollAmendmentReviews(input: {
  author: BrowserContext;
  reviewer: BrowserContext;
  company: string;
  amendment: string;
  month: string;
}) {
  const { author, reviewer, company, amendment, month } = input,
    base = `/api/v1/companies/${company}/payroll`,
    editor = await author.newPage(),
    checker = await reviewer.newPage(),
    submissions: { path: string; operation: string; body: unknown }[] = [],
    decisions: { operation: string; body: unknown }[] = [];
  await editor.route(`**${base}/amendments/${amendment}/reviews/*`, async (route) => {
    const request = route.request();
    if (request.method() !== "PUT") return route.continue();
    submissions.push({
      path: new URL(request.url()).pathname,
      operation: request.headers()["idempotency-key"] ?? "",
      body: request.postDataJSON(),
    });
    if (submissions.length !== 1) return route.continue();
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    await route.abort("failed");
  });
  try {
    await editor.goto(
      `/payroll/amendments/${amendment}/reviews?company=${company}&year=${month.slice(0, 4)}`,
    );
    await editor.getByRole("link", { name: "Submit for review", exact: true }).click();
    await editor
      .getByRole("textbox", { name: "Reason", exact: true })
      .fill("Submit the retained correction");
    await editor.getByRole("button", { name: "Submit for review", exact: true }).click();
    await editor.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Amendment submitted for review.");
    expect(submissions).toHaveLength(2);
    expect(submissions[0]?.operation).toBeTruthy();
    expect(submissions[1]).toEqual(submissions[0]);
    await editor
      .getByRole("status")
      .getByRole("link", { name: "View review", exact: true })
      .click();
    const firstId = z.uuid().parse(new URL(editor.url()).pathname.split("/").at(-1)),
      firstResponse = await author.request.get(`${base}/amendment-reviews/${firstId}`);
    expect(firstResponse.status()).toBe(200);
    const initial = payrollAmendmentReviewDetailsDto.parse(await firstResponse.json());
    expect(initial.amendment.version).toBe(2);
    expect(initial.review).toMatchObject({ amendmentVersion: 1, version: 0, status: "PENDING" });
    expect(initial.availableActions).toEqual(["WITHDRAW"]);
    await editor.getByRole("link", { name: "Withdraw", exact: true }).click();
    await editor
      .getByRole("textbox", { name: "Reason", exact: true })
      .fill("Review the captured correction again");
    await editor.getByRole("button", { name: "Withdraw", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Review withdrawn.");
    await editor
      .getByRole("status")
      .getByRole("link", { name: "View review", exact: true })
      .click();
    await editor.getByRole("link", { name: "All amendment reviews", exact: true }).click();
    await expect(
      editor.getByRole("table", { name: "Amendment reviews", exact: true }),
    ).toContainText("Withdrawn");
    await editor.getByRole("link", { name: "Submit for review", exact: true }).click();
    await editor
      .getByRole("textbox", { name: "Reason", exact: true })
      .fill("Submit the reviewed correction again");
    await editor.getByRole("button", { name: "Submit for review", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Amendment submitted for review.");
    await editor
      .getByRole("status")
      .getByRole("link", { name: "View review", exact: true })
      .click();
    const reviewId = z.uuid().parse(new URL(editor.url()).pathname.split("/").at(-1));
    expect(reviewId).not.toBe(firstId);
    expect(submissions).toHaveLength(3);
    const reviewPath = `${base}/amendment-reviews/${reviewId}`,
      submittedResponse = await reviewer.request.get(reviewPath);
    expect(submittedResponse.status()).toBe(200);
    const submitted = payrollAmendmentReviewDetailsDto.parse(await submittedResponse.json());
    expect(submitted.review).toMatchObject({ number: 2, amendmentVersion: 3, status: "PENDING" });
    expect(submitted.calculation).toEqual(initial.calculation);
    await checker.goto(`/approvals/${submitted.approval.id}?company=${company}`);
    await checker.getByRole("link", { name: "View source request", exact: true }).click();
    await expect(checker).toHaveURL(
      new RegExp(`/payroll/amendment-reviews/${reviewId}\\?company=`, "u"),
    );
    await checker.route(
      `**${base}/amendments/${amendment}/reviews/${reviewId}/decisions`,
      async (route) => {
        const request = route.request();
        decisions.push({
          operation: request.headers()["idempotency-key"] ?? "",
          body: request.postDataJSON(),
        });
        if (decisions.length !== 1) return route.continue();
        const response = await route.fetch();
        expect(response.status()).toBe(200);
        await route.abort("failed");
      },
    );
    await checker.getByRole("link", { name: "Approve", exact: true }).click();
    await checker
      .getByRole("textbox", { name: "Reason", exact: true })
      .fill("Independent correction review");
    await checker.getByRole("button", { name: "Approve", exact: true }).click();
    await checker.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect(checker.getByRole("status")).toContainText("Decision recorded.");
    expect(decisions).toHaveLength(2);
    expect(decisions[0]?.operation).toBeTruthy();
    expect(decisions[1]).toEqual(decisions[0]);
    await checker
      .getByRole("status")
      .getByRole("link", { name: "View review", exact: true })
      .click();
    await expect(checker.getByRole("region", { name: "Overview", exact: true })).toContainText(
      "Approved",
    );
    await checker.getByRole("tab", { name: "History", exact: true }).click();
    await expect(checker.getByRole("table", { name: "History", exact: true })).toContainText(
      "Independent correction review",
    );
    await checker.screenshot({
      path: "../../.work/dashboard-payroll-amendment-review-real.png",
      fullPage: true,
      animations: "disabled",
    });
    await checker.getByRole("link", { name: "Reviewed calculation", exact: true }).click();
    await expect(checker).toHaveURL(/revision=3/u);
    await expect(
      checker.getByRole("table", { name: "Monthly comparison", exact: true }),
    ).toContainText(month);
    const approvedResponse = await reviewer.request.get(reviewPath),
      retainedResponse = await author.request.get(`${base}/amendment-reviews/${firstId}`),
      directoryResponse = await author.request.get(`${base}/amendments/${amendment}/reviews`);
    expect(approvedResponse.status()).toBe(200);
    expect(retainedResponse.status()).toBe(200);
    expect(directoryResponse.status()).toBe(200);
    const approved = payrollAmendmentReviewDetailsDto.parse(await approvedResponse.json()),
      retained = payrollAmendmentReviewDetailsDto.parse(await retainedResponse.json());
    expect(payrollAmendmentReviewsDto.parse(await directoryResponse.json())).toHaveLength(2);
    expect(retained.review.status).toBe("WITHDRAWN");
    expect(retained.history).toHaveLength(2);
    expect(approved.review).toMatchObject({ status: "APPROVED", version: 1 });
    expect(approved.amendment.version).toBe(5);
    expect(approved.history).toHaveLength(2);
    return {
      reviewId,
      amendmentVersion: approved.amendment.version,
      reviewVersion: approved.review.version,
      approvalVersion: approved.approval.version,
      calculationId: approved.calculation.id,
    };
  } finally {
    await Promise.all([editor.close(), checker.close()]);
  }
}
