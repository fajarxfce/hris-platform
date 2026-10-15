import { expect, type Page, test } from "@playwright/test";
import { calculationCompany } from "./fixtures/payroll-calculations";
import { payslipId } from "./fixtures/payroll-payslips";
import { installPayrollPayslipsApi } from "./payroll-payslips-api";

const detail = `/payroll/payslips/${payslipId()}?company=${calculationCompany}&from=2026-01&until=2026-12&scope=AUTHORIZED&query=Example`;
const viewedRevision = (page: Page, label = "Payslip revision", title = "Overview") =>
  page
    .getByRole("region", { name: title, exact: true })
    .getByText(label, { exact: true })
    .locator("..")
    .locator("dd");

test("revision links retain filters, browser history and the exact exported statement", async ({
  page,
}) => {
  const api = await installPayrollPayslipsApi(page, { currentRevision: 2 });
  await page.goto(detail);
  await expect(viewedRevision(page)).toHaveText("2");
  await expect(page.getByRole("link", { name: "Next revision", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Previous revision", exact: true }).click();
  await expect(viewedRevision(page)).toHaveText("1");
  await page.getByRole("link", { name: "Next revision", exact: true }).click();
  await expect(viewedRevision(page)).toHaveText("2");
  await page.getByRole("link", { name: "Original revision", exact: true }).click();
  await expect(viewedRevision(page)).toHaveText("0");
  await expect(page.getByRole("link", { name: "Previous revision", exact: true })).toHaveCount(0);
  await page.getByRole("tab", { name: "Payment history", exact: true }).click();
  await expect(viewedRevision(page, "Payslip revision", "Payment summary")).toHaveText("2");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(viewedRevision(page)).toHaveText("0");
  const completed = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download PDF", exact: true }).click();
  expect((await completed).suggestedFilename()).toBe(`payslip-${payslipId()}-v0-id.pdf`);
  const pdf = api.reads.findLast((url) => url.pathname.endsWith("/pdf"));
  expect(pdf?.searchParams.get("revision")).toBe("0");
  expect(pdf?.searchParams.get("scope")).toBe("AUTHORIZED");
  await page.getByRole("link", { name: "Latest revision", exact: true }).click();
  await expect(viewedRevision(page)).toHaveText("2");
  expect(new URL(page.url()).searchParams.has("revision")).toBe(false);
  await page.goBack();
  await expect(viewedRevision(page)).toHaveText("0");
  await page.getByRole("link", { name: "All payslips", exact: true }).click();
  await expect(page.getByLabel("Employee name or number", { exact: true })).toHaveValue("Example");
  expect(new URL(page.url()).searchParams.has("revision")).toBe(false);
  expect(api.identity.unhandled).toEqual([]);
});

test("new payment evidence offers an explicit latest revision without replacing the open statement", async ({
  page,
}) => {
  const api = await installPayrollPayslipsApi(page);
  await page.goto(`${detail}&tab=payments`);
  await expect(viewedRevision(page)).toHaveText("0");
  api.publishRevision(1);
  await page.getByRole("button", { name: "Refresh payments", exact: true }).click();
  await expect(
    page.getByText("A newer payslip revision is available.", { exact: true }),
  ).toBeVisible();
  await expect(viewedRevision(page)).toHaveText("0");
  expect(api.count("get")).toBe(1);
  await page.getByRole("link", { name: "Latest revision", exact: true }).click();
  await expect(viewedRevision(page)).toHaveText("1");
  expect(api.count("get")).toBe(2);
});

test("a replaced pending revision cannot restore an older statement", async ({ page }) => {
  const api = await installPayrollPayslipsApi(page, { currentRevision: 2 });
  await page.goto(detail);
  await expect(viewedRevision(page)).toHaveText("2");
  const held = api.holdNext("get");
  try {
    await page.getByRole("link", { name: "Previous revision", exact: true }).click();
    await held.entered;
    await page.getByRole("link", { name: "Latest revision", exact: true }).click();
    await expect(viewedRevision(page)).toHaveText("2");
    held.release();
    await expect(viewedRevision(page)).toHaveText("2");
    expect(new URL(page.url()).searchParams.has("revision")).toBe(false);
  } finally {
    held.release();
  }
});

test("revision replacement cancels a pending PDF without a late browser download", async ({
  page,
}) => {
  const api = await installPayrollPayslipsApi(page, { currentRevision: 2 });
  await page.goto(detail);
  await expect(viewedRevision(page)).toHaveText("2");
  const held = api.holdNext("pdf"),
    downloads: string[] = [];
  page.on("download", (download) => downloads.push(download.suggestedFilename()));
  try {
    await page.getByRole("button", { name: "Download PDF", exact: true }).click();
    await held.entered;
    const cancelled = page.waitForEvent("requestfailed", (request) =>
      new URL(request.url()).pathname.endsWith("/pdf"),
    );
    await page.getByRole("link", { name: "Previous revision", exact: true }).click();
    await expect(viewedRevision(page)).toHaveText("1");
    held.release();
    await cancelled;
    await expect(page.getByRole("button", { name: "Download PDF", exact: true })).toBeEnabled();
    expect(downloads).toEqual([]);
    expect(api.count("pdf")).toBe(1);
  } finally {
    held.release();
  }
});

for (const selection of ["revision=bad", "revision=-1", "revision=0&revision=1"]) {
  test(`invalid ${selection} never falls back to a current network read`, async ({ page }) => {
    const api = await installPayrollPayslipsApi(page, { currentRevision: 2 });
    await page.goto(`${detail}&${selection}`);
    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page.getByRole("region", { name: "Overview", exact: true })).toHaveCount(0);
    expect(api.count("get")).toBe(0);
    expect(api.count("payments")).toBe(0);
    await page.getByRole("link", { name: "Latest revision", exact: true }).click();
    await expect(viewedRevision(page)).toHaveText("2");
  });
}

test("a missing retained revision requires explicit recovery and does not display another version", async ({
  page,
}) => {
  const api = await installPayrollPayslipsApi(page, { currentRevision: 2 });
  await page.goto(`${detail}&revision=99`);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("button", { name: "Download PDF", exact: true })).toHaveCount(0);
  expect(api.count("get")).toBe(1);
  expect(
    api.reads.find((url) => url.searchParams.has("revision"))?.searchParams.get("revision"),
  ).toBe("99");
  await page.getByRole("link", { name: "Latest revision", exact: true }).click();
  await expect(viewedRevision(page)).toHaveText("2");
});

test("a retained statement remains readable when current payment acquisition fails", async ({
  page,
}) => {
  const api = await installPayrollPayslipsApi(page, { currentRevision: 2 });
  api.failNext("payments", "connection_unavailable");
  await page.goto(`${detail}&revision=0&tab=payments`);
  await expect(viewedRevision(page)).toHaveText("0");
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("link", { name: "Next revision", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Download PDF", exact: true })).toBeEnabled();
  await page.getByRole("link", { name: "Latest revision", exact: true }).click();
  await expect(viewedRevision(page)).toHaveText("2");
});

test("retained self scope survives locale, theme and narrow revision navigation", async ({
  page,
}) => {
  const api = await installPayrollPayslipsApi(page, {
    currentRevision: 2,
    permissions: ["payroll.self.read"],
  });
  await page.goto(`${detail}&revision=1`);
  await expect(viewedRevision(page)).toHaveText("1");
  const reads = api.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(viewedRevision(page, "Revisi payslip", "Ringkasan")).toHaveText("1");
  expect(api.reads).toHaveLength(reads);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  await page.getByRole("link", { name: "Revisi sebelumnya", exact: true }).click();
  await expect(viewedRevision(page, "Revisi payslip", "Ringkasan")).toHaveText("0");
  expect(api.reads.every((url) => url.searchParams.get("scope") === "SELF")).toBe(true);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-payslip-revision-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
