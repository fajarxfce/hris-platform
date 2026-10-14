import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { calculationCompany } from "./fixtures/payroll-calculations";
import { payslipId } from "./fixtures/payroll-payslips";
import { companyIds } from "./identity-api";
import { installPayrollPayslipsApi } from "./payroll-payslips-api";

const directory = `/payroll/payslips?company=${calculationCompany}&from=2026-01&until=2026-12&scope=AUTHORIZED`;
const detail = `/payroll/payslips/${payslipId()}?company=${calculationCompany}&from=2026-01&until=2026-12&scope=AUTHORIZED`;
test("submitted search and page navigation retain bounded company filters", async ({ page }) => {
  const api = await installPayrollPayslipsApi(page, { count: 51 });
  await page.goto(directory);
  const table = page.getByRole("table", { name: "Payslips", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await expect(table).toContainText("Example employee 51");
  const reads = api.reads.length;
  await page.getByLabel("Employee name or number", { exact: true }).fill("EMP-0002");
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(table).toContainText("Example employee 2");
  expect(api.reads.at(-1)?.searchParams.has("after")).toBe(false);
  await page
    .getByRole("button", { name: "View payslip: Example employee 2 · 2026-09", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Take-home pay", exact: true })).toContainText(
    "10,000,000.75",
  );
  await page.screenshot({
    path: "../../.work/dashboard-payslip-light.png",
    animations: "disabled",
  });
  await page.getByRole("link", { name: "All payslips", exact: true }).click();
  await expect(page.getByLabel("Employee name or number", { exact: true })).toHaveValue("EMP-0002");
  expect(api.identity.unhandled).toEqual([]);
});
test("a self-only reader starts on payslips with explicit self scope and no finance navigation", async ({
  page,
}) => {
  const api = await installPayrollPayslipsApi(page, { permissions: ["payroll.self.read"] });
  await page.goto(`/payroll?company=${calculationCompany}`);
  await expect(
    page.getByRole("table", { name: "Payslips", exact: true }).getByRole("row"),
  ).toHaveCount(2);
  await expect(page.getByLabel("Scope", { exact: true })).toHaveValue("SELF");
  await expect(page.getByLabel("Scope", { exact: true })).toBeDisabled();
  await expect(
    page.getByRole("link", { name: /^(Finalization|Payroll periods|Compensation)$/u }),
  ).toHaveCount(0);
  await page.goto(detail);
  await expect(page.getByRole("button", { name: "Download PDF", exact: true })).toBeEnabled();
  expect(api.reads.every((url) => url.searchParams.get("scope") === "SELF")).toBe(true);
});
test("finance can narrow to owned payslips without retaining a company-wide cursor", async ({
  page,
}) => {
  const api = await installPayrollPayslipsApi(page, { count: 51 });
  await page.goto(directory);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await page.getByLabel("Scope", { exact: true }).selectOption("SELF");
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Payslips", exact: true }).getByRole("row"),
  ).toHaveCount(2);
  await expect(page.getByRole("cell", { name: "Example employee 1", exact: true })).toBeVisible();
  await expect.poll(() => api.reads.at(-1)?.searchParams.get("scope")).toBe("SELF");
  expect(api.reads.at(-1)?.searchParams.has("after")).toBe(false);
});
test("payment refresh, tabs and locale changes preserve the immutable detail owner", async ({
  page,
}) => {
  const api = await installPayrollPayslipsApi(page);
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Tax withheld", exact: true })).toContainText(
    "-IDR",
  );
  await page.getByRole("tab", { name: "Payment history", exact: true }).click();
  await expect(page.getByRole("table", { name: /Payment history/u })).toContainText("Pending");
  const attempt = api.payments.attempts[0];
  if (!attempt) throw new Error("Payment fixture missing");
  attempt.status = "SUCCEEDED";
  attempt.resolvedAt = "2026-10-02T03:00:00Z";
  api.payments.version++;
  await page.getByRole("button", { name: "Refresh payments", exact: true }).click();
  await expect(page.getByRole("table", { name: /Payment history/u })).toContainText("Paid");
  expect(api.count("get")).toBe(1);
  const reads = api.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByRole("tab", { name: "Riwayat pembayaran", exact: true })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  await page.getByRole("tab", { name: "Perhitungan", exact: true }).click();
  await expect(page.getByRole("table", { name: "Penghasilan", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  expect(api.reads).toHaveLength(reads);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  const takeHome = page.getByRole("region", { name: "Take-home pay", exact: true }).locator("p");
  await expect
    .poll(() =>
      takeHome.evaluate(
        (element) =>
          element.scrollHeight <= Number.parseFloat(getComputedStyle(element).lineHeight) + 1,
      ),
    )
    .toBe(true);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-payslip-dark-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
test("PDF generation uses an explicit language, bounded download and a single pending request", async ({
  page,
}) => {
  const api = await installPayrollPayslipsApi(page);
  await page.goto(detail);
  await page.getByLabel("PDF language", { exact: true }).selectOption("EN");
  const held = api.holdNext("pdf");
  try {
    const downloaded = page.waitForEvent("download");
    await page.getByRole("button", { name: "Download PDF", exact: true }).click();
    await held.entered;
    await expect(
      page.getByRole("button", { name: "Preparing download", exact: true }),
    ).toBeDisabled();
    await expect(page.getByLabel("PDF language", { exact: true })).toBeDisabled();
    held.release();
    const file = await downloaded;
    expect(file.suggestedFilename()).toBe(`payslip-${payslipId()}-en.pdf`);
    const path = await file.path();
    if (!path) throw new Error("Browser download required");
    expect((await readFile(path)).subarray(0, 5).toString()).toBe("%PDF-");
    await expect(page.getByRole("status")).toContainText("PDF sent to your browser's downloads.");
    expect(api.count("pdf")).toBe(1);
    expect(
      api.reads.find((url) => url.pathname.endsWith("/pdf"))?.searchParams.get("language"),
    ).toBe("EN");
  } finally {
    held.release();
  }
});
test("payment or PDF failure keeps the payslip readable and waits for an explicit retry", async ({
  page,
}) => {
  const api = await installPayrollPayslipsApi(page);
  api.failNext("payments", "request_timeout");
  await page.goto(detail);
  await page.getByRole("tab", { name: "Payment history", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Example employee 1",
  );
  await page.getByRole("button", { name: "Refresh payments", exact: true }).click();
  await expect(page.getByRole("table", { name: /Payment history/u })).toBeVisible();
  api.failNext("pdf", "payroll_pdf_text_unsupported", 422);
  await page.getByRole("button", { name: "Download PDF", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("PDF font");
  expect(api.count("pdf")).toBe(1);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toBeVisible();
  await expect(page.locator("body")).not.toContainText("PRIVATE PAYROLL DIAGNOSTIC");
});
test("revocation during payment refresh clears private detail and download controls", async ({
  page,
}) => {
  const api = await installPayrollPayslipsApi(page);
  await page.goto(detail);
  await page.getByRole("tab", { name: "Payment history", exact: true }).click();
  await expect(page.getByRole("table", { name: /Payment history/u })).toBeVisible();
  api.deny();
  await page.getByRole("button", { name: "Refresh payments", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Download PDF", exact: true })).toHaveCount(0);
  await expect(page.locator("body")).not.toContainText("Example employee 1");
});
test("company replacement discards a pending directory response and its search", async ({
  page,
}) => {
  const api = await installPayrollPayslipsApi(page);
  await page.goto(`${directory}&query=Example`);
  await expect(page.getByRole("table", { name: "Payslips", exact: true })).toBeVisible();
  const held = api.holdNext("list");
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("status")).toContainText("No published payslips");
    held.release();
    await expect(page.getByLabel("Employee name or number", { exact: true })).toHaveValue("");
    await expect(page.locator("body")).not.toContainText("Example employee 1");
    expect(api.reads.at(-1)?.pathname).toContain(companyIds[1]);
  } finally {
    held.release();
  }
});
