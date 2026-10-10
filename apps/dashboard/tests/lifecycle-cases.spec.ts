import { expect, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import {
  installLifecycleCasesApi,
  lifecycleCaseId,
  lifecycleCaseSeed,
} from "./lifecycle-cases-api";

const catalog = `/people/lifecycle/cases?company=${companyIds[0]}&status=OPEN`;
const detail = `/people/lifecycle/cases/${lifecycleCaseId()}?company=${companyIds[0]}&status=OPEN`;
const queue = `/people/lifecycle/tasks?company=${companyIds[0]}`;

test("case navigation preserves bounded pages, filters and minimal employee context", async ({
  page,
}) => {
  const api = await installLifecycleCasesApi(page);
  await page.goto("/people/lifecycle/cases");
  await expect(page).toHaveURL(catalog);
  const table = page.getByRole("table", { name: "Cases", exact: true });
  await expect(table.getByRole("row")).toHaveCount(11);
  await expect(table).toContainText("North employee 1");
  await expect(table).toContainText("E001");
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(11);
  await expect(table).toContainText("North employee 11");
  await expect(page).toHaveURL(new RegExp(`after=${lifecycleCaseId(0, 10)}$`, "u"));
  await page.goBack();
  await expect(table.getByRole("row")).toHaveCount(11);
  await table
    .getByRole("button", { name: "Open case: North employee 1 · E001", exact: true })
    .click();
  await expect(page.getByRole("heading", { name: "North employee 1", exact: true })).toBeVisible();
  expect(api.reads.filter((url) => url.pathname.endsWith("/history"))).toEqual([]);
  await page.getByRole("link", { name: "Back to cases", exact: true }).click();
  await page.getByLabel("Status", { exact: true }).selectOption("COMPLETED");
  await expect(page.getByRole("status")).toHaveText("No lifecycle cases match these filters.");
  await expect(page).not.toHaveURL(/after=/u);
  await page.goto(`${catalog}&employmentId=${lifecycleCaseSeed(0, 1).employmentId}`);
  await expect(table.getByRole("row")).toHaveCount(2);
  await page.getByRole("button", { name: "Show all employees", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(11);
  expect(api.identity.unhandled).toEqual([]);
});

test("case history loads on demand, pages by version and opens immutable change details", async ({
  page,
}) => {
  const api = await installLifecycleCasesApi(page);
  await page.goto(detail);
  const tasks = page.getByRole("table", { name: "Checklist", exact: true });
  await expect(tasks).toBeVisible();
  await tasks.getByRole("button", { name: "View task: Review equipment", exact: true }).click();
  const drawer = page.getByRole("dialog", { name: "Review equipment", exact: true });
  await expect(drawer).toContainText("North employee 1");
  await expect(drawer).toContainText("You");
  await drawer.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("tab", { name: "History", exact: true }).click();
  const history = page.getByRole("table", { name: "History", exact: true });
  await expect(history.getByRole("row")).toHaveCount(51);
  expect(api.completedReads.filter((url) => url.pathname.endsWith("/history"))).toHaveLength(1);
  await page
    .getByRole("group", { name: "History pages", exact: true })
    .getByRole("button", { name: "Next page", exact: true })
    .click();
  await expect(history.getByRole("row")).toHaveCount(2);
  await expect(page).toHaveURL(/historyAfter=49$/u);
  await history.getByRole("button", { name: "View change: 50", exact: true }).click();
  const change = page.getByRole("dialog", { name: "Change details", exact: true });
  await expect(change).toContainText("Equipment verification");
  await change.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("tab", { name: "Overview", exact: true }).click();
  await expect(tasks).toBeVisible();
  expect(api.completedReads.filter((url) => /\/cases\/[^/]+$/u.test(url.pathname))).toHaveLength(1);
  expect(api.identity.unhandled).toEqual([]);
});

test("perform-only users see their pending queue without acquiring company cases or profiles", async ({
  page,
}) => {
  const api = await installLifecycleCasesApi(page, { permissions: ["people.lifecycle.perform"] });
  await page.goto("/");
  await page.getByRole("link", { name: "Assigned tasks", exact: true }).click();
  const table = page.getByRole("table", { name: "Assigned tasks", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await expect(page.getByRole("link", { name: "Lifecycle cases", exact: true })).toHaveCount(0);
  await table
    .getByRole("button", { name: "View task: Review access · North employee 1", exact: true })
    .click();
  const drawer = page.getByRole("dialog", { name: "Review access", exact: true });
  await expect(drawer).toContainText("E001");
  await drawer.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await expect(table).toContainText("Welcome session");
  await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  expect(api.reads.every((url) => url.pathname.endsWith("/tasks/assigned"))).toBe(true);
  expect(api.identity.unhandled).toEqual([]);
});

test("a company switch discards a held case read and resets employee and cursor filters", async ({
  page,
}) => {
  const api = await installLifecycleCasesApi(page);
  await page.goto(detail);
  await expect(page.getByRole("heading", { name: "North employee 1", exact: true })).toBeVisible();
  const held = api.holdNextRead();
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Cases", exact: true })).toContainText(
      "South employee 1",
    );
    held.release();
    await expect(page.getByRole("main")).not.toContainText("North employee");
    await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}&status=OPEN$`, "u"));
  } finally {
    held.release();
  }
  expect(api.identity.unhandled).toEqual([]);
});

test("direct links check grants and reject malformed case and queue cursors before I/O", async ({
  page,
}) => {
  const api = await installLifecycleCasesApi(page, { permissions: ["people.lifecycle.perform"] });
  await page.goto(detail);
  await expect(page.getByRole("alert")).toContainText("do not have access");
  expect(api.reads).toEqual([]);
  await page.goto(`${queue}&after=invalid`);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toEqual([]);
  api.identity.setPermissions(["people.lifecycle.read"]);
  await page.goto(`/people/lifecycle/cases/invalid?company=${companyIds[0]}&status=OPEN`);
  await expect(page.getByRole("alert")).toContainText("unavailable in the selected company");
  await page.goto(queue);
  await expect(page.getByRole("alert")).toContainText("do not have access");
  expect(api.reads).toEqual([]);
  expect(api.identity.unhandled).toEqual([]);
});

test("a revoked history read removes the enclosing case and exposes only safe error copy", async ({
  page,
}) => {
  const api = await installLifecycleCasesApi(page);
  await page.goto(detail);
  await expect(page.getByRole("heading", { name: "North employee 1", exact: true })).toBeVisible();
  api.failReads("access_denied");
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("do not have access");
  await expect(page.getByRole("main")).not.toContainText("North employee 1");
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE LIFECYCLE DIAGNOSTIC");
  api.failReads(null);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("table", { name: "History", exact: true })).toBeVisible();
  expect(api.completedReads).toHaveLength(4);
  expect(api.identity.unhandled).toEqual([]);
});

test("case details adapt to theme, Indonesian and narrow screens without reloading data", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const api = await installLifecycleCasesApi(page);
  await page.goto(detail);
  await expect(page.getByRole("table", { name: "Checklist", exact: true })).toBeVisible();
  const reads = api.reads.length;
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-case-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-case-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByRole("region", { name: "Ringkasan", exact: true })).toContainText(
    "Nomor karyawan",
  );
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-case-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("tab", { name: "Riwayat", exact: true }).click();
  await expect(page.getByRole("table", { name: "Riwayat", exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  expect(errors).toEqual([]);
  expect(api.identity.unhandled).toEqual([]);
});
