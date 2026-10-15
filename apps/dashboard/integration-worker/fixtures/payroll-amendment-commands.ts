import { randomUUID } from "node:crypto";
import { type BrowserContext, expect } from "@playwright/test";
import { payrollAmendmentDetailsDto } from "../../src/features/payroll/data/models/payroll-amendment-dto";

/** Recover a committed calculation through the browser using its original operation. */
export async function exercisePayrollAmendmentCalculation(input: {
  author: BrowserContext;
  company: string;
  amendment: string;
  month: string;
}) {
  const { author, company, amendment, month } = input,
    page = await author.newPage(),
    base = `/api/v1/companies/${company}/payroll/amendments/${amendment}`;
  const writes: { operation: string; body: unknown }[] = [];
  await page.route(`**${base}/calculate`, async (route) => {
    const request = route.request();
    writes.push({
      operation: request.headers()["idempotency-key"] ?? "",
      body: request.postDataJSON(),
    });
    if (writes.length !== 1) return route.continue();
    const committed = await route.fetch();
    expect(committed.status()).toBe(200);
    await route.abort("failed");
  });
  try {
    await page.goto(
      `/payroll/amendments/${amendment}?company=${company}&year=${month.slice(0, 4)}`,
    );
    await page.getByRole("link", { name: "Calculate proposal", exact: true }).click();
    await page
      .getByRole("textbox", { name: "Reason", exact: true })
      .fill("Calculate corrected retained month");
    await page.getByRole("button", { name: "Calculate proposal", exact: true }).click();
    await page.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Calculation attempt recorded.");
    expect(writes).toHaveLength(2);
    expect(writes[0]?.operation).toBeTruthy();
    expect(writes[1]).toEqual(writes[0]);
    expect(writes[0]?.body).toEqual({
      expectedVersion: 0,
      reason: "Calculate corrected retained month",
    });
    await page
      .getByRole("status")
      .getByRole("link", { name: "View proposal", exact: true })
      .click();
    await expect(page).toHaveURL(/revision=1/u);
    await expect(
      page.getByRole("table", { name: "Monthly comparison", exact: true }),
    ).toContainText(month);
    const current = await author.request.get(base);
    expect(current.status()).toBe(200);
    const retained = payrollAmendmentDetailsDto.parse(await current.json());
    expect(retained.amendment.summary).toMatchObject({ version: 1, status: "CALCULATED" });
    expect(retained.latestCalculation).toMatchObject({
      number: 1,
      draftVersion: 0,
      status: "SUCCEEDED",
    });
    expect(retained.history.items).toHaveLength(2);
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({
      path: "../../.work/dashboard-payroll-amendment-calculation-real.png",
      fullPage: true,
      animations: "disabled",
    });
  } finally {
    await page.close();
  }
}

/** Cancel a separate reviewed draft without removing its original terms and history. */
export async function exercisePayrollAmendmentCancellation(input: {
  author: BrowserContext;
  company: string;
  amendment: string;
  headers: () => Record<string, string>;
}) {
  const { author, company, amendment, headers } = input,
    base = `/api/v1/companies/${company}/payroll/amendments`,
    current = await author.request.get(`${base}/${amendment}`);
  expect(current.status()).toBe(200);
  const original = payrollAmendmentDetailsDto.parse(await current.json()),
    cancelled = randomUUID();
  const saved = await author.request.put(`${base}/${cancelled}`, {
    headers: headers(),
    data: {
      ...original.amendment.terms,
      firstAssessmentId: original.amendment.summary.firstAssessmentId,
      expectedVersion: null,
      reason: "Separate correction for cancellation review",
    },
  });
  expect(saved.status()).toBe(200);
  const page = await author.newPage();
  try {
    await page.goto(`/payroll/amendments/${cancelled}?company=${company}`);
    await page.getByRole("link", { name: "Cancel proposal", exact: true }).click();
    await page
      .getByRole("textbox", { name: "Reason", exact: true })
      .fill("Withdraw duplicate proposed correction");
    await page.getByRole("button", { name: "Cancel proposal", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Proposal cancelled.");
    await page
      .getByRole("status")
      .getByRole("link", { name: "View proposal", exact: true })
      .click();
    await expect(page).toHaveURL(/revision=1/u);
    await expect(page.getByRole("region", { name: "Summary", exact: true })).toContainText(
      "Cancelled",
    );
    await expect(
      page.getByRole("link", { name: /^(Edit proposal|Calculate proposal|Cancel proposal)$/u }),
    ).toHaveCount(0);
    await page.getByRole("tab", { name: "History", exact: true }).click();
    await expect(
      page.getByRole("table", { name: "History", exact: true }).getByRole("row"),
    ).toHaveCount(3);
    const response = await author.request.get(`${base}/${cancelled}`);
    expect(response.status()).toBe(200);
    const retained = payrollAmendmentDetailsDto.parse(await response.json());
    expect(retained.amendment.summary).toMatchObject({ version: 1, status: "CANCELLED" });
    expect(retained.amendment.terms).toEqual(original.amendment.terms);
    expect(retained.history.items.map((row) => row.action)).toEqual(["SAVED", "CANCELLED"]);
  } finally {
    await page.close();
  }
}
