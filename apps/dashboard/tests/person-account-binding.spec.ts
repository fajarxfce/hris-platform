import { expect, test } from "@playwright/test";
import { bindingTarget, installAccountBindingApi } from "./person-account-binding-api";
import { profileEmployeeIds } from "./person-profile-api";

const profilePath = `/people/employees/${profileEmployeeIds[0]}/profile`;
const bindingPath = `/people/employees/${profileEmployeeIds[0]}/account-link`;

test("employee linking filters ineligible accounts and recovers the original command after response loss", async ({
  page,
}) => {
  const api = await installAccountBindingApi(page, { emptyFirstPage: true });
  await page.goto(profilePath);
  await page.getByRole("link", { name: "Link account", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No eligible accounts on this page.");
  await expect(page.getByText("Reviewer account", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await page.getByRole("button", { name: "Select account: Employee account", exact: true }).click();
  await page
    .getByLabel("Reason for linking", { exact: true })
    .fill("Reviewed employee account evidence");
  await page.screenshot({
    path: "../../.work/dashboard-account-binding.png",
    fullPage: true,
    animations: "disabled",
  });
  api.loseNext();
  await page.getByRole("button", { name: "Link account", exact: true }).click();
  await expect(
    page.getByText("The binding outcome is not confirmed. Check it using the same submission."),
  ).toBeVisible();
  expect(api.commits).toBe(1);
  await expect(page.getByLabel("Reason for linking", { exact: true })).toHaveAttribute(
    "readonly",
    "",
  );
  api.rejectNext("stale_version");
  await page.getByRole("button", { name: "Check binding", exact: true }).click();
  await expect(page.getByRole("button", { name: "Check binding", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Check binding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Account linked.");
  expect(api.commits).toBe(1);
  expect(api.commands).toHaveLength(3);
  expect(api.commands.map((command) => [command.operation, command.body])).toEqual(
    Array.from({ length: 3 }, () => [api.commands[0]?.operation, api.commands[0]?.body]),
  );
  await page.getByRole("link", { name: "View profile", exact: true }).click();
  await expect(page.getByRole("region", { name: "Personal data", exact: true })).toContainText(
    bindingTarget,
  );
  await expect(page.getByRole("link", { name: "Link account", exact: true })).toHaveCount(0);
});

test("dirty binding reviews can stay on the page and localize without clearing the selected account", async ({
  page,
}) => {
  await installAccountBindingApi(page);
  await page.goto(bindingPath);
  await page.getByRole("button", { name: "Select account: Employee account", exact: true }).click();
  await page.getByLabel("Reason for linking", { exact: true }).fill("Reviewed evidence");
  await page.getByRole("link", { name: "Back to profile", exact: true }).click();
  const decision = page.getByRole("dialog", { name: "Leave this page?", exact: true });
  await expect(decision).toBeVisible();
  await decision.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Alasan pengaitan", { exact: true })).toHaveValue(
    "Reviewed evidence",
  );
  await expect(page.getByRole("region", { name: "Akun terpilih", exact: true })).toContainText(
    bindingTarget,
  );
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test("direct linking routes cannot bypass missing permission", async ({ page }) => {
  const api = await installAccountBindingApi(page, { allowed: false });
  await page.goto(bindingPath);
  await expect(page.getByRole("alert")).toContainText(
    "Your account cannot link employee accounts.",
  );
  await expect(page.getByLabel("Reason for linking", { exact: true })).toHaveCount(0);
  expect(api.commands).toHaveLength(0);
});

test("a direct linking route cannot replace an existing employee binding", async ({ page }) => {
  const api = await installAccountBindingApi(page);
  api.profile.replaceProfile({ accountId: bindingTarget });
  await page.goto(bindingPath);
  await expect(page.getByRole("alert")).toContainText("already has a linked account");
  await expect(page.getByLabel("Reason for linking", { exact: true })).toHaveCount(0);
  expect(api.commands).toHaveLength(0);
});
