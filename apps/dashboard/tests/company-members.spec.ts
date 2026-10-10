import { expect, test } from "@playwright/test";
import { installCompanyMembersApi, memberId } from "./company-members-api";
import { companyIds } from "./identity-api";

test("company accounts keep bounded pages and show grant sources in localized responsive views", async ({
  page,
}) => {
  const api = await installCompanyMembersApi(page, { longPage: true });
  await page.goto("/administration/members");
  const table = page.getByRole("table", { name: "Account directory", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await expect(table).toContainText("Nusa member 51");
  await page.goBack();
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "View access: Nusa member 1", exact: true }).click();
  const details = page.getByRole("region", { name: "Account access", exact: true });
  await expect(details).toContainText("account1@example.invalid");
  await expect(page.getByRole("region", { name: "Applied roles", exact: true })).toContainText(
    "Employee access",
  );
  await page.screenshot({
    path: "../../.work/dashboard-company-member.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByRole("region", { name: "Akses akun", exact: true })).toContainText(
    "Permission langsung",
  );
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  expect(
    api.reads
      .filter((url) => !url.pathname.endsWith(memberId))
      .every((url) => url.searchParams.get("limit") === "50"),
  ).toBe(true);
  expect(api.identity.unhandled).toEqual([]);
});

test("denied directory access performs no membership reads", async ({ page }) => {
  const api = await installCompanyMembersApi(page, { allowed: false });
  await page.goto("/administration/members");
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("link", { name: "Company accounts", exact: true })).toHaveCount(0);
  await expect(page.getByText("Nusa member 1", { exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(0);
});

test("refresh failures remove previously displayed account and role data", async ({ page }) => {
  const api = await installCompanyMembersApi(page);
  await page.goto(`/administration/members/${memberId}`);
  await expect(page.getByRole("region", { name: "Account access", exact: true })).toContainText(
    "account1@example.invalid",
  );
  api.fail("company_member_not_found");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByText("account1@example.invalid", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Applied roles", exact: true })).toHaveCount(0);
  await expect(page.getByText("PRIVATE SERVER DETAIL", { exact: true })).toHaveCount(0);
});

test("company changes abandon pending account reads and discard old cursors", async ({ page }) => {
  const api = await installCompanyMembersApi(page);
  await page.goto("/administration/members");
  await expect(page.getByRole("table", { name: "Account directory", exact: true })).toContainText(
    "Nusa member 1",
  );
  const pending = api.holdNextRead();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await pending.start;
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(page.getByRole("table", { name: "Account directory", exact: true })).toContainText(
    "Lintas member",
  );
  pending.release();
  await expect(page.getByText("Nusa member 1", { exact: true })).toHaveCount(0);
  await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}`, "u"));
});
