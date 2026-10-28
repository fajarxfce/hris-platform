import { expect, test } from "@playwright/test";
import { companyIds, installIdentityApi } from "./identity-api";
import { installLeaveReports } from "./leave-report-api";

const permissions = ["company.read", "reports.read", "leave.read"];
const path = "/reports/leave?from=2024-01-01&until=2024-12-31";

test("annual leave totals preserve applied filters and localized half days, with narrow-screen status drill-down", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installLeaveReports(page);
  await page.goto(path);
  await expect(page.getByRole("heading", { name: "Leave report", exact: true })).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Submitted days in period", exact: true }),
  ).toContainText("25.5");
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "1",
  );
  const reads = api.reads.length;
  await page.getByLabel("From date", { exact: true }).fill("2024-01-02");
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect.poll(() => api.reads.at(-1)?.searchParams.get("from")).toBe("2024-01-02");
  await page.goBack();
  await expect(page.getByLabel("From date", { exact: true })).toHaveValue("2024-01-01");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("region", { name: "Hari pengajuan dalam periode", exact: true }),
  ).toContainText("25,5");
  const open = page.getByRole("button", { name: "Lihat pengajuan: Menunggu", exact: true });
  await expect(open).toBeVisible();
  expect(
    await open.evaluate((element) => {
      const rect = element.getBoundingClientRect();
      return rect.left >= 0 && rect.right <= window.innerWidth;
    }),
  ).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-leave-report-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await open.click();
  await expect(
    page.getByRole("heading", { name: "Pengajuan dalam laporan cuti", exact: true }),
  ).toBeVisible();
  const rows = page.getByRole("table", { name: "Pengajuan", exact: true });
  await expect(rows.getByRole("row")).toHaveCount(51);
  expect(new URL(page.url()).searchParams.get("status")).toBe("PENDING");
  expect(new URL(page.url()).searchParams.get("company")).toBe(companyIds[0]);
  await page.getByRole("button", { name: "Halaman berikutnya", exact: true }).click();
  await expect(rows.getByRole("row")).toHaveCount(2);
  await expect(rows).toContainText("Report employee 51");
  await page.getByRole("button", { name: "Halaman pertama", exact: true }).click();
  await expect(rows.getByRole("row")).toHaveCount(51);
  await page.getByRole("link", { name: "Kembali ke laporan", exact: true }).click();
  await expect(page.getByLabel("Dari tanggal", { exact: true })).toHaveValue("2024-01-01");
});

test("failed refreshes clear both totals and private request rows until explicit retry succeeds", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installLeaveReports(page);
  await page.goto(path);
  await expect(
    page.getByRole("region", { name: "Submitted days in period", exact: true }),
  ).toBeVisible();
  api.fail(true);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Submitted days in period", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE LEAVE REPORT");
  api.fail(false);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page.getByRole("button", { name: "View requests: Pending", exact: true }).click();
  await expect(page.getByRole("main")).toContainText("Report employee 1");
  api.fail(true);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("main")).not.toContainText("Report employee");
  api.fail(false);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("main")).toContainText("Report employee 1");
});

test("company replacement resets report filters and removes the preceding company's rows", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installLeaveReports(page);
  await page.goto(`/reports/leave/requests?from=2024-01-01&until=2024-12-31&status=PENDING`);
  await expect(page.getByRole("main")).toContainText("Report employee 1");
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(page.getByRole("status")).toContainText("No requests match this period and status.");
  await expect(page.getByRole("main")).not.toContainText("Report employee");
  expect(new URL(page.url()).searchParams.has("status")).toBe(false);
  expect(api.reads.at(-1)?.searchParams.get("from")).not.toBe("2024-01-01");
});

test("source grants and invalid annual windows prevent report acquisition", async ({ page }) => {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    permissions: ["company.read", "reports.read"],
  });
  const api = await installLeaveReports(page);
  await page.goto(path);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(0);
  await expect(page.getByRole("link", { name: "Leave report", exact: true })).toHaveCount(0);
  identity.setPermissions(permissions);
  await page.reload();
  await expect(page.getByRole("heading", { name: "Leave report", exact: true })).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Submitted days in period", exact: true }),
  ).toBeVisible();
  const count = api.reads.length;
  await page.getByLabel("From date", { exact: true }).fill("2023-12-31");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("main")).toContainText("Choose a date range of up to 366 days");
  expect(api.reads).toHaveLength(count);
});
