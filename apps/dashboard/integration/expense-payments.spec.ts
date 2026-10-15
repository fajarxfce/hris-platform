import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import type { ExpensePaymentReviewDto } from "../src/features/expenses/data/models/expense-payment-dto";
import type { ExpensePaymentPreparationDto } from "../src/features/expenses/data/models/expense-payment-preparation-dto";
import type { ExpenseReconciliationDto } from "../src/features/expenses/data/models/expense-reconciliation-dto";
import { createAdminCompany } from "./fixtures/admin-company";
import { bankConfirmationInput } from "./fixtures/bank-confirmation-input";
import {
  paymentAccounts,
  paymentHeaders,
  paymentSession,
  prepareExpensePaymentClaims,
} from "./fixtures/expense-payment-scenario";

test("real finance prepares, releases and reconciles payments with original-command recovery and current scope", async ({
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
    const makerCsrf = await paymentSession(makerPage, "maker");
    await paymentSession(finance, "checker");
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
    const preparationWrites: {
      id: string;
      operation: string;
      body: ExpensePaymentPreparationDto;
    }[] = [];
    await makerPage.route(`**${base}/expenses/payments/*`, async (route) => {
      if (route.request().method() !== "PUT") return route.fallback();
      const id = new URL(route.request().url()).pathname.split("/").at(-1);
      if (!id) throw new Error("Preparation batch ID is required");
      preparationWrites.push({
        id,
        operation: route.request().headers()["idempotency-key"] ?? "",
        body: route.request().postDataJSON() as ExpensePaymentPreparationDto,
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      expect(await response.json()).toMatchObject({ id, version: 0 });
      if (preparationWrites.length === 1) return route.abort("failed");
      return route.fulfill({ response });
    });
    const directory = `/expenses/payments?company=${company}&from=${today}&until=${today}`;
    await makerPage.goto(directory);
    await makerPage.getByRole("button", { name: "Prepare payment batch", exact: true }).click();
    await makerPage.getByLabel("Batch title", { exact: true }).fill("October reimbursements");
    await makerPage
      .getByLabel("Preparation note", { exact: true })
      .fill("Bank destinations verified");
    await makerPage.getByRole("button", { name: "Add claims", exact: true }).click();
    const picker = makerPage.getByRole("dialog", { name: "Select approved claims", exact: true });
    for (const [index, item] of items.entries())
      await picker
        .getByRole("button", {
          name: `Add claim: Customer visit ${index + 1} · ${item.name}`,
          exact: true,
        })
        .click();
    await picker.getByRole("button", { name: "Done", exact: true }).click();
    for (const [index, item] of items.entries()) {
      await makerPage
        .getByRole("button", {
          name: `Edit bank details: Customer visit ${index + 1} · ${item.name}`,
          exact: true,
        })
        .click();
      const panel = makerPage.getByRole("dialog", { name: "Bank details", exact: true });
      await panel.getByLabel("Bank code", { exact: true }).fill(item.destination.bankCode);
      await panel
        .getByLabel("Account number", { exact: true })
        .fill(item.destination.accountNumber);
      await panel.getByLabel("Account name", { exact: true }).fill(item.destination.accountName);
      await panel.getByRole("button", { name: "Save bank details", exact: true }).click();
    }
    await makerPage.getByRole("button", { name: "Review instructions", exact: true }).click();
    await makerPage
      .getByRole("heading", { name: "Review payment batch", exact: true })
      .scrollIntoViewIfNeeded();
    await makerPage.screenshot({
      path: "../../.work/dashboard-expense-preparation-real.png",
      fullPage: true,
      animations: "disabled",
    });
    await makerPage.getByRole("button", { name: "Create batch", exact: true }).click();
    await makerPage
      .getByRole("button", { name: "Retry original preparation", exact: true })
      .click();
    await expect(makerPage.getByRole("status")).toContainText("Payment batch prepared.");
    expect(preparationWrites).toHaveLength(2);
    expect(preparationWrites[1]).toEqual(preparationWrites[0]);
    const id = preparationWrites[0]?.id;
    if (!id) throw new Error("Prepared batch receipt is required");
    const makerReview = (await (
      await maker.request.get(`${base}/expenses/payments/${id}/review`)
    ).json()) as ExpensePaymentReviewDto;
    expect(makerReview.availableActions).toEqual(["CANCEL"]);
    expect(makerReview.batch).toMatchObject({
      status: "PREPARED",
      version: 0,
      totalAmount: "425001.00",
    });
    expect(makerReview.history.items).toHaveLength(1);
    for (const item of items) {
      const preparedItem = makerReview.batch.items.find(
        (entry) => entry.submissionId === item.submissionId,
      );
      if (!preparedItem) throw new Error("Prepared instruction is required");
      expect(preparedItem.destination).toEqual(item.destination);
      item.id = preparedItem.id;
    }
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
    const releasedResponse = await checker.request.get(`${base}/expenses/payments/${id}/review`);
    expect(releasedResponse.status()).toBe(200);
    const released = (await releasedResponse.json()) as ExpensePaymentReviewDto;
    if (!released.batch.releasedAt) throw new Error("Released payment time is required");
    const confirmation = await bankConfirmationInput(released.batch.releasedAt, "Asia/Jakarta");
    const reconciliationWrites: { operation: string; body: ExpenseReconciliationDto }[] = [];
    await finance.route(`**${base}/expenses/payments/${id}/reconcile`, async (route) => {
      const body = route.request().postDataJSON() as ExpenseReconciliationDto;
      reconciliationWrites.push({
        operation: route.request().headers()["idempotency-key"] ?? "",
        body,
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      expect(await response.json()).toMatchObject({ id, version: body.expectedVersion + 1 });
      if (reconciliationWrites.length === 1) return route.abort("failed");
      return route.fulfill({ response });
    });
    await finance.getByRole("button", { name: "Reconcile payments", exact: true }).click();
    await finance
      .getByRole("button", { name: "Edit result: Payment Employee 1 · PAY-1", exact: true })
      .click();
    const outcomePanel = finance.getByRole("dialog", { name: "Bank result", exact: true });
    await outcomePanel
      .getByRole("combobox", { name: "Bank result", exact: true })
      .selectOption("SUCCEEDED");
    await outcomePanel
      .getByLabel("Bank transaction reference", { exact: true })
      .fill("BROWSER-BANK-001");
    await outcomePanel
      .getByLabel("Bank confirmation time (Asia/Jakarta)", { exact: true })
      .fill(confirmation);
    await outcomePanel.getByLabel("Result note", { exact: true }).fill("Bank statement confirmed");
    await outcomePanel.getByRole("button", { name: "Add to reconciliation", exact: true }).click();
    await expect(outcomePanel).toHaveCount(0);
    await finance
      .getByLabel("Reconciliation note", { exact: true })
      .fill("First transfer confirmed");
    await finance.getByRole("button", { name: "Submit results", exact: true }).click();
    await finance
      .getByRole("button", { name: "Retry original reconciliation", exact: true })
      .click();
    await expect(finance.getByRole("status")).toContainText("Payment results recorded.");
    expect(reconciliationWrites).toHaveLength(2);
    expect(reconciliationWrites[1]).toEqual(reconciliationWrites[0]);
    expect(reconciliationWrites[0]?.body.results[0]).toMatchObject({
      itemId: first.id,
      status: "SUCCEEDED",
      transactionReference: "BROWSER-BANK-001",
      confirmedNoTransfer: false,
    });
    await finance.getByRole("link", { name: "Batch details", exact: true }).last().click();
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
    await finance.getByRole("button", { name: "Reconcile payments", exact: true }).click();
    await expect(
      finance.getByRole("button", { name: "Edit result: Payment Employee 1 · PAY-1", exact: true }),
    ).toHaveCount(0);
    await finance
      .getByRole("button", { name: "Edit result: Payment Employee 2 · PAY-2", exact: true })
      .click();
    await outcomePanel
      .getByRole("combobox", { name: "Bank result", exact: true })
      .selectOption("FAILED");
    await outcomePanel
      .getByLabel("Bank confirmation time (Asia/Jakarta)", { exact: true })
      .fill(confirmation);
    await outcomePanel
      .getByLabel("Result note", { exact: true })
      .fill("Bank confirmed no funds transferred");
    await outcomePanel.getByRole("checkbox").check();
    await finance.screenshot({
      path: "../../.work/dashboard-expense-reconciliation-real-result.png",
      fullPage: true,
      animations: "disabled",
    });
    await outcomePanel.getByRole("button", { name: "Add to reconciliation", exact: true }).click();
    await expect(outcomePanel).toHaveCount(0);
    await finance
      .getByLabel("Reconciliation note", { exact: true })
      .fill("Remaining bank outcome confirmed");
    await finance.getByRole("button", { name: "Submit results", exact: true }).click();
    await expect(finance.getByRole("status")).toContainText("Payment results recorded.");
    expect(reconciliationWrites).toHaveLength(3);
    expect(reconciliationWrites[2]?.body).toMatchObject({
      expectedVersion: 2,
      results: [
        {
          itemId: second.id,
          status: "FAILED",
          transactionReference: null,
          confirmedNoTransfer: true,
        },
      ],
    });
    await finance.getByRole("link", { name: "Batch details", exact: true }).last().click();
    await expect(
      finance.getByRole("button", { name: "Reconcile payments", exact: true }),
    ).toHaveCount(0);
    await finance.getByRole("tab", { name: "Reconciliation", exact: true }).click();
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
