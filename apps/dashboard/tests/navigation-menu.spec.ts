import { expect, test } from "@playwright/test";
import { employeeId, installAttendanceApi } from "./attendance-api";

test("grouped navigation searches locally and keeps its active destination on detail pages", async ({
  page,
}) => {
  const api = await installAttendanceApi(page);
  await page.goto(`/workforce/attendance/${employeeId}/days/2026-10-12`);
  await expect(page.getByRole("heading", { name: "North Employee", exact: true })).toBeVisible();
  const menu = page.getByRole("navigation", { name: "Navigation", exact: true });
  await expect(menu.locator('[aria-current="page"]')).toHaveCount(1);
  await expect(menu.getByRole("link", { name: "Attendance", exact: true })).toHaveAttribute(
    "aria-current",
    "page",
  );
  await expect(
    menu.getByRole("heading", { name: "Time and attendance", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Find a menu", { exact: true }).fill("attendance");
  await expect(menu.getByRole("link", { name: "Overview", exact: true })).toHaveCount(0);
  await expect(menu.getByRole("link", { name: "Attendance", exact: true })).toBeVisible();
  await page.getByLabel("Find a menu", { exact: true }).fill("missing menu");
  await expect(menu.getByRole("status")).toHaveText("No results");
  await page.getByRole("button", { name: "Navigation", exact: true }).click();
  await expect(menu.getByRole("link", { name: "Overview", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Navigation", exact: true }).click();
  await page.getByLabel("Find a menu", { exact: true }).fill("");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(
    page.getByRole("heading", { name: "Waktu kerja dan presensi", exact: true }),
  ).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  expect(api.identity.unhandled).toHaveLength(0);
});

test("a hidden desktop search cannot remove destinations from the narrow rail", async ({
  page,
}) => {
  await installAttendanceApi(page);
  await page.goto(`/workforce/attendance/${employeeId}/days/2026-10-12`);
  await expect(page.getByRole("heading", { name: "North Employee", exact: true })).toBeVisible();
  await page.getByLabel("Find a menu", { exact: true }).fill("missing menu");
  const menu = page.getByRole("navigation", { name: "Navigation", exact: true });
  await expect(menu.getByRole("link")).toHaveCount(0);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(menu.getByRole("link", { name: "Attendance", exact: true })).toBeVisible();
  await page.setViewportSize({ width: 1200, height: 844 });
  await expect(page.getByLabel("Find a menu", { exact: true })).toHaveValue("missing menu");
  await expect(menu.getByRole("status")).toHaveText("No results");
});
