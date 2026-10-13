import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import type { ExpensePaymentReviewDto } from "../src/features/expenses/data/models/expense-payment-dto";
import { createAdminCompany } from "./fixtures/admin-company";
import {
  paymentAccounts,
  paymentHeaders,
  paymentSession,
  prepareExpensePaymentClaims,
} from "./fixtures/expense-payment-scenario";

test("real finance review exports confirmed instructions and shows partial and final reconciliation with current scope", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  test.setTimeout(180_000);
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-expense-payment-admin@example.invalid",
    "PAY_BROWSER",
    "Browser Expense Payments",
  );
  const { base, today, items } = await prepareExpensePaymentClaims(context, company, csrf);
  expect(
    (await context.request.get(`${base}/expenses/payments?from=${today}&until=${today}`)).status(),
  ).toBe(403);
  if (!baseURL) throw new Error("Integration base URL is required");
  const reviewer = await browser.newContext({ baseURL }),
    maker = await browser.newContext({ baseURL }),
    checker = await browser.newContext({ baseURL });
  try {
    const reviewPage = await reviewer.newPage(),
      makerPage = await maker.newPage(),
      finance = await checker.newPage();
    const reviewerCsrf = await paymentSession(reviewPage, "reviewer");
    for (const item of items) {
      const approved = await reviewer.request.post(
        `${base}/expenses/submissions/${item.submissionId}/decisions`,
        {
          headers: paymentHeaders(reviewerCsrf),
          data: {
            expectedVersion: 1,
            expectedApprovalVersion: 0,
            decision: "APPROVE",
            reason: "Reviewed travel evidence",
            acknowledgeDuplicates: false,
          },
        },
      );
      expect(approved.status()).toBe(200);
    }
    const makerCsrf = await paymentSession(makerPage, "maker"),
      checkerCsrf = await paymentSession(finance, "checker"),
      id = randomUUID();
    const cancelledId = randomUUID();
    const replacement = await maker.request.put(`${base}/expenses/payments/${cancelledId}`, {
      headers: paymentHeaders(makerCsrf),
      data: {
        title: "Reimbursement instructions to replace",
        items: items.map((item) => ({
          id: randomUUID(),
          submissionId: item.submissionId,
          destination: item.destination,
        })),
        reason: "Initial bank instructions",
      },
    });
    expect(replacement.status()).toBe(200);
    await makerPage.goto(`/expenses/payments/${cancelledId}?company=${company}`);
    await expect(makerPage.getByRole("button", { name: "Release batch", exact: true })).toHaveCount(
      0,
    );
    await makerPage.getByRole("button", { name: "Cancel batch", exact: true }).click();
    await makerPage
      .getByLabel("Reason", { exact: true })
      .fill("Replace bank instructions before release");
    await makerPage.getByRole("button", { name: "Cancel batch", exact: true }).click();
    await expect(makerPage.getByRole("status")).toContainText("Payment batch updated.");
    const cancelled = (await (
      await maker.request.get(`${base}/expenses/payments/${cancelledId}/review`)
    ).json()) as ExpensePaymentReviewDto;
    expect(cancelled.batch).toMatchObject({ status: "CANCELLED", version: 1 });
    expect(cancelled.history.items).toHaveLength(2);
    const prepared = await maker.request.put(`${base}/expenses/payments/${id}`, {
      headers: paymentHeaders(makerCsrf),
      data: {
        title: "October reimbursements",
        items: items.map((item) => ({
          id: item.id,
          submissionId: item.submissionId,
          destination: item.destination,
        })),
        reason: "Bank destinations verified",
      },
    });
    expect(prepared.status()).toBe(200);
    const makerReview = (await (
      await maker.request.get(`${base}/expenses/payments/${id}/review`)
    ).json()) as ExpensePaymentReviewDto;
    expect(makerReview.availableActions).toEqual(["CANCEL"]);
    const directory = `/expenses/payments?company=${company}&from=${today}&until=${today}`;
    await finance.goto(directory);
    await expect(
      finance.getByRole("table", { name: "Expense payments", exact: true }),
    ).toContainText("425,001.00");
    await finance
      .getByRole("button", { name: "View batch: October reimbursements", exact: true })
      .click();
    const overview = finance.getByRole("region", { name: "Payment batch", exact: true });
    await expect(overview).toContainText("Prepared");
    await finance.getByRole("tab", { name: "Bank instructions", exact: true }).click();
    await expect(finance.getByRole("button", { name: "Download CSV", exact: true })).toHaveCount(0);
    const releaseWrites: { operation: string; body: unknown }[] = [];
    await finance.route(`**${base}/expenses/payments/${id}/release`, async (route) => {
      releaseWrites.push({
        operation: route.request().headers()["idempotency-key"] ?? "",
        body: route.request().postDataJSON(),
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      expect(await response.json()).toMatchObject({ id, version: 1 });
      if (releaseWrites.length === 1) return route.abort("failed");
      return route.fulfill({ response });
    });
    await finance.getByRole("button", { name: "Release batch", exact: true }).click();
    await finance.getByLabel("Reason", { exact: true }).fill("Independent bank instruction review");
    await finance.getByRole("button", { name: "Release batch", exact: true }).click();
    await expect(
      finance.getByRole("button", { name: "Retry original action", exact: true }),
    ).toBeVisible();
    await finance.getByRole("button", { name: "Retry original action", exact: true }).click();
    await expect(finance.getByRole("status")).toContainText("Payment batch updated.");
    expect(releaseWrites).toHaveLength(2);
    expect(releaseWrites[1]).toEqual(releaseWrites[0]);
    await finance.getByRole("link", { name: "Batch details", exact: true }).last().click();
    await finance.getByRole("tab", { name: "Bank instructions", exact: true }).click();
    const started = finance.waitForEvent("download");
    await finance.getByRole("button", { name: "Download CSV", exact: true }).click();
    const download = await started,
      path = await download.path();
    expect(path).not.toBeNull();
    expect(download.suggestedFilename()).toBe(`expense-payments-${id}.csv`);
    if (!path) throw new Error("CSV artifact is required");
    const csv = await readFile(path, "utf8");
    for (const item of items) {
      expect(csv).toContain(`"${item.id}"`);
      expect(csv).toContain(`"'${item.destination.accountNumber}"`);
      expect(csv).toContain(`"${item.amount}"`);
    }
    await finance
      .getByRole("button", { name: "View payment: Payment Employee 1 · PAY-1", exact: true })
      .click();
    await expect(finance.getByRole("dialog")).toContainText("001234567890");
    await finance.screenshot({
      path: "../../.work/dashboard-expense-payment-real-instruction.png",
      fullPage: true,
      animations: "disabled",
    });
    await finance.getByRole("button", { name: "Close", exact: true }).click();
    const [first, second] = items;
    if (!first || !second) throw new Error("Two fixture payments are required");
    const settled = await checker.request.post(`${base}/expenses/payments/${id}/reconcile`, {
      headers: paymentHeaders(checkerCsrf),
      data: {
        expectedVersion: 1,
        results: [
          {
            itemId: first.id,
            status: "SUCCEEDED",
            transactionReference: "BROWSER-BANK-001",
            occurredAt: new Date().toISOString(),
            reason: "Bank statement confirmed",
            confirmedNoTransfer: false,
          },
        ],
        reason: "First transfer confirmed",
      },
    });
    expect(settled.status()).toBe(200);
    await finance.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(finance.getByRole("button", { name: "Download CSV", exact: true })).toHaveCount(0);
    await finance.getByRole("tab", { name: "Overview", exact: true }).click();
    await expect(overview).toContainText("Released");
    await expect(finance.getByRole("region", { name: "Pending", exact: true })).toContainText("1");
    expect((await checker.request.get(`${base}/expenses/payments/${id}/export`)).status()).toBe(
      409,
    );
    await finance.getByRole("tab", { name: "Reconciliation", exact: true }).click();
    await expect(finance.getByRole("table", { name: "Reconciliation", exact: true })).toContainText(
      "BROWSER-BANK-001",
    );
    const finished = await checker.request.post(`${base}/expenses/payments/${id}/reconcile`, {
      headers: paymentHeaders(checkerCsrf),
      data: {
        expectedVersion: 2,
        results: [
          {
            itemId: second.id,
            status: "FAILED",
            transactionReference: null,
            occurredAt: new Date().toISOString(),
            reason: "Bank confirmed no funds transferred",
            confirmedNoTransfer: true,
          },
        ],
        reason: "Remaining bank outcome confirmed",
      },
    });
    expect(finished.status()).toBe(200);
    await finance.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(
      finance.getByRole("table", { name: "Reconciliation", exact: true }).getByRole("row"),
    ).toHaveCount(3);
    await finance
      .getByRole("button", { name: "View payment: Payment Employee 2 · PAY-2", exact: true })
      .click();
    await expect(finance.getByRole("dialog")).toContainText("Bank confirmed no funds transferred");
    await finance.getByRole("button", { name: "Close", exact: true }).click();
    await finance.getByRole("tab", { name: "Overview", exact: true }).click();
    await expect(overview).toContainText("Closed");
    await finance.getByRole("tab", { name: "Activity", exact: true }).click();
    await expect(
      finance.getByRole("table", { name: "Activity", exact: true }).getByRole("row"),
    ).toHaveCount(5);
    await finance.screenshot({
      path: "../../.work/dashboard-expense-payment-real-history.png",
      fullPage: true,
      animations: "disabled",
    });
    const other = await context.request.post("/api/v1/companies", {
      headers: paymentHeaders(csrf),
      data: { code: "PAY_OTHER", name: "Other Finance Company", timezone: "Asia/Jakarta" },
    });
    expect(other.status()).toBe(200);
    const otherCompany = ((await other.json()) as { id: string }).id;
    const otherGrant = await context.request.put(
      `/api/v1/companies/${otherCompany}/members/${paymentAccounts.checker}`,
      {
        headers: paymentHeaders(csrf),
        data: {
          active: true,
          permissions: ["company.read", "expenses.pay"],
          reason: "Isolated finance company",
        },
      },
    );
    expect(otherGrant.status()).toBe(200);
    expect(
      (
        await checker.request.get(
          `/api/v1/companies/${otherCompany}/expenses/payments/${id}/review`,
        )
      ).status(),
    ).toBe(404);
    const revoked = await context.request.put(`${base}/members/${paymentAccounts.checker}`, {
      headers: paymentHeaders(csrf),
      data: {
        active: true,
        expectedVersion: 0,
        permissions: ["company.read"],
        reason: "Verify finance access revocation",
      },
    });
    expect(revoked.status()).toBe(200);
    await finance.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(finance.getByRole("alert")).toBeVisible();
    await expect(finance.getByRole("table")).toHaveCount(0);
    expect((await checker.request.get(`${base}/expenses/payments/${id}/review`)).status()).toBe(
      403,
    );
    const final = (await (
      await maker.request.get(`${base}/expenses/payments/${id}/review`)
    ).json()) as ExpensePaymentReviewDto;
    expect(final.batch).toMatchObject({ status: "CLOSED", version: 3, totalAmount: "425001.00" });
    expect(final.results).toHaveLength(2);
    expect(final.history.items).toHaveLength(4);
  } finally {
    await reviewer.close();
    await maker.close();
    await checker.close();
  }
});
