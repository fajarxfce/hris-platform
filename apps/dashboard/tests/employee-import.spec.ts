import { expect, test } from "@playwright/test";
import {
  employeeImportPermissions,
  importId,
  installEmployeeImportApi,
} from "./employee-import-api";
import { companyIds } from "./identity-api";

const list = `/people/imports?company=${companyIds[0]}`;
const details = `/people/imports/${importId(0, 1)}?company=${companyIds[0]}`;

test("imports provide bounded directories, coherent count summaries and on-demand proposals and attempts", async ({
  page,
}) => {
  const api = await installEmployeeImportApi(page);
  await page.goto(list);
  await expect(page.getByRole("table", { name: "Employee imports", exact: true })).toContainText(
    "north-10.csv",
  );
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(page.getByRole("table")).toContainText("north-12.csv");
  await page.goBack();
  await page.getByRole("button", { name: "View import: north-1.csv", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Ready for review",
  );
  await expect(page.getByRole("region", { name: "Rows", exact: true })).toContainText("25");
  expect(
    api.reads.some((url) => url.pathname.endsWith("/rows") || url.pathname.endsWith("/attempts")),
  ).toBe(false);
  await page.getByRole("tab", { name: "Rows", exact: true }).click();
  await expect(page.getByRole("table", { name: "Rows", exact: true })).toContainText(
    "North employee 25",
  );
  await page.getByRole("button", { name: "View row: 1 · EMP001", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("Use a valid ISO date");
  await expect(page.getByRole("dialog")).toContainText("A proposal could not be created");
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "View row: 1 · EMP001", exact: true }),
  ).toBeFocused();
  await page.getByRole("button", { name: "View row: 2 · EMP002", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("+10000-01-01");
  await expect(page.getByRole("dialog")).toContainText("between 1900 and 2200");
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(page.getByRole("table")).toContainText("North employee 27");
  await page.getByRole("tab", { name: "Attempts", exact: true }).click();
  await expect(page.getByRole("table", { name: "Attempts", exact: true })).toContainText("Preview");
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  await page.getByRole("tab", { name: "Rows", exact: true }).click();
  await expect(page.getByRole("table")).toContainText("North employee 27");
  expect(api.identity.unhandled).toEqual([]);
});
for (const missing of employeeImportPermissions) {
  test(`private import views require ${missing} before any feature I/O`, async ({ page }) => {
    const api = await installEmployeeImportApi(
      page,
      employeeImportPermissions.filter((permission) => permission !== missing),
    );
    await page.goto(`${details}&tab=rows`);
    await expect(page.getByRole("alert")).toContainText(
      "You do not have access to employee imports.",
    );
    await expect(page.getByRole("link", { name: "Employee imports", exact: true })).toHaveCount(0);
    await expect(page.getByRole("tab")).toHaveCount(0);
    expect(api.reads).toEqual([]);
  });
}
test("invalid row and attempt cursors are rejected without acquiring those pages", async ({
  page,
}) => {
  const api = await installEmployeeImportApi(page);
  await page.goto(`${details}&tab=rows&rowsAfter=5001`);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads.some((url) => url.pathname.endsWith("/rows"))).toBe(false);
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await expect(page.getByRole("table")).toContainText("North employee 1");
  await page.goto(`${details}&tab=attempts&attemptsAfter=invalid`);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads.some((url) => url.pathname.endsWith("/attempts"))).toBe(false);
});
test("a pending private row read cannot populate a different company workspace", async ({
  page,
}) => {
  const api = await installEmployeeImportApi(page);
  await page.goto(details);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toBeVisible();
  const pending = api.hold("rows");
  await page.getByRole("tab", { name: "Rows", exact: true }).click();
  await pending.entered;
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(page).toHaveURL(`/people/imports?company=${companyIds[1]}`);
  pending.release();
  await expect(page.getByRole("table")).toContainText("south-1.csv");
  await expect(page.getByText("North employee 1", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "View import: south-1.csv", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "south-1.csv",
  );
  expect(api.identity.unhandled).toEqual([]);
});
test("revoking row access clears summary and proposal data until an explicit refresh", async ({
  page,
}) => {
  const api = await installEmployeeImportApi(page);
  await page.goto(`${details}&tab=rows`);
  await page.getByRole("button", { name: "View row: 2 · EMP002", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("private@example.invalid");
  await page.getByRole("button", { name: "Close", exact: true }).click();
  api.fail("rows", "employee_import_access_required");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "You do not have access to employee imports.",
  );
  await expect(page.getByRole("heading", { name: "north-1.csv", exact: true })).toHaveCount(0);
  await expect(page.getByRole("tab")).toHaveCount(0);
  await expect(page.getByText("private@example.invalid")).toHaveCount(0);
  await expect(page.getByText("PRIVATE TECHNICAL MESSAGE")).toHaveCount(0);
  api.recover();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("table")).toContainText("North employee 1");
});
test("localized dark mobile row details contain scrolling and preserve keyboard access", async ({
  page,
}) => {
  const api = await installEmployeeImportApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${details}&tab=rows`);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.getByRole("button", { name: "Lihat baris: 2 · EMP002", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText(
    "Tanggal employment harus antara tahun 1900 dan 2200.",
  );
  await expect(page.getByRole("dialog")).toContainText("Kewarganegaraan");
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-employee-import-mobile.png",
    animations: "disabled",
  });
  const bounds = await page.getByRole("dialog").evaluate((element) => {
    const rect = element.getBoundingClientRect();
    return { left: rect.left, right: rect.right, available: document.documentElement.clientWidth };
  });
  expect(bounds.left).toBeGreaterThanOrEqual(-1);
  expect(bounds.right).toBeLessThanOrEqual(bounds.available + 1);
  await page.keyboard.press("Escape");
  await expect(
    page.getByRole("button", { name: "Lihat baris: 2 · EMP002", exact: true }),
  ).toBeFocused();
  expect(api.identity.unhandled).toEqual([]);
});
