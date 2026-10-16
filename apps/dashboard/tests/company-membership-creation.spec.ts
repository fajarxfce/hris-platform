import { expect, type Page, test } from "@playwright/test";
import { installCompanyMembershipCreationApi } from "./company-membership-creation-api";
import { managedAccountId } from "./fixtures/managed-accounts";
import { roleTemplateId } from "./fixtures/role-templates";
import { companyIds } from "./identity-api";

async function openEditor(page: Page, editor: string) {
  await page.goto(editor);
  await expect(page.getByRole("button", { name: "Grant access", exact: true })).toBeVisible();
}
async function selectRole(page: Page) {
  const drawer = page.getByRole("dialog", { name: "Choose role", exact: true });
  await drawer
    .getByRole("button", {
      name: "Use this version: Operations reviewer 1 (REVIEWER_1)",
      exact: true,
    })
    .click();
  await expect(drawer).toHaveCount(0);
}

test("company account selection excludes unavailable accounts and grants reviewed current roles", async ({
  page,
}) => {
  const f = await installCompanyMembershipCreationApi(page);
  await page.goto(`/administration/members?company=${companyIds[0]}`);
  await page.getByRole("link", { name: "Add account", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Choose account: Example Colleague 2", exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Choose account: Example Colleague 3", exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Choose account: Sample Reviewer", exact: true }),
  ).toBeDisabled();
  const listingCount = () =>
    f.reads.filter((url) => url.pathname === "/api/v1/identity/accounts").length;
  expect(listingCount()).toBe(1);
  await page
    .getByRole("searchbox", { name: "Find an account", exact: true })
    .fill("colleague1@example.invalid");
  expect(listingCount()).toBe(1);
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect.poll(listingCount).toBe(2);
  await page
    .getByRole("button", { name: "Choose account: Example Colleague 1", exact: true })
    .click();
  await expect(
    page.getByText(
      "This access applies to the selected company. Employee profiles are linked separately.",
      { exact: true },
    ),
  ).toBeVisible();
  await page.getByRole("button", { name: "Grant access", exact: true }).click();
  await expect(page.getByText("Check this value.", { exact: true })).toBeVisible();
  expect(f.writes).toHaveLength(0);
  await page.getByRole("button", { name: "Add role", exact: true }).click();
  await selectRole(page);
  await expect(page.getByText("Selected version: 21", { exact: true })).toBeVisible();
  await page
    .getByRole("region", { name: "Direct permissions", exact: true })
    .locator("summary")
    .filter({ hasText: "Leave" })
    .click();
  await page.getByRole("checkbox", { name: "View leave", exact: true }).check();
  await page
    .getByLabel("Reason", { exact: true })
    .fill("Reviewed initial company responsibilities");
  await page.evaluate(() => scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-company-account-assignment.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Grant access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  expect(f.writes).toHaveLength(1);
  expect(f.writes[0]?.body).toEqual({
    expectedVersion: null,
    active: true,
    permissions: ["leave.read"],
    roleTemplates: [{ id: roleTemplateId(), version: 21, retainApplied: false }],
    reason: "Reviewed initial company responsibilities",
  });
  await page.getByRole("link", { name: "View access", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Example Colleague 1", exact: true }),
  ).toBeVisible();
  expect(f.memberships.get(companyIds[0])?.get(managedAccountId())?.member.version).toBe(0);
  expect(f.identity.unhandled).toEqual([]);
});

test("candidate pages stay bounded and changing selection retains the explicit search", async ({
  page,
}) => {
  const f = await installCompanyMembershipCreationApi(page, { longPage: true });
  await page.goto(f.picker);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Choose account: Example Colleague 51", exact: true }),
  ).toBeVisible();
  const listing = f.reads.filter((url) => url.pathname === "/api/v1/identity/accounts");
  expect(listing[1]?.searchParams.get("after")).toBe(managedAccountId(49));
  await page.getByRole("searchbox", { name: "Find an account", exact: true }).fill("Colleague 1");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await page
    .getByRole("button", { name: "Choose account: Example Colleague 1", exact: true })
    .click();
  await expect(page.getByRole("button", { name: "Grant access", exact: true })).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill("Draft for review");
  await page.getByRole("link", { name: "Choose another account", exact: true }).click();
  await page.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("searchbox", { name: "Find an account", exact: true })).toHaveValue(
    "Colleague 1",
  );
  expect(f.writes).toHaveLength(0);
});

test("existing company membership opens its review without overwriting any grants", async ({
  page,
}) => {
  const f = await installCompanyMembershipCreationApi(page);
  f.assignExisting();
  await page.goto(f.editor);
  await expect(page.getByRole("alert")).toContainText("This account already has company access.");
  await page.getByRole("link", { name: "View existing access", exact: true }).click();
  await expect(page.getByRole("link", { name: "Edit company access", exact: true })).toBeVisible();
  expect(f.writes).toHaveLength(0);
  expect(f.memberships.get(companyIds[0])?.get(managedAccountId())?.member.version).toBe(4);
});

test("a lost creation response retains the same operation through MFA and a later version rejection", async ({
  page,
}) => {
  const f = await installCompanyMembershipCreationApi(page);
  await openEditor(page, f.editor);
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed initial access");
  f.dropNext();
  await page.getByRole("button", { name: "Grant access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is unconfirmed.");
  f.identity.expireMfa();
  f.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  f.rejectNext("stale_version");
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is unconfirmed.");
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  expect(f.writes).toHaveLength(4);
  for (const command of f.writes) expect(command).toEqual(f.writes[0]);
  expect(f.memberships.get(companyIds[0])?.get(managedAccountId())?.member.version).toBe(0);
});

test("a concurrent assignment requires protected review of the existing membership", async ({
  page,
}) => {
  const f = await installCompanyMembershipCreationApi(page);
  await openEditor(page, f.editor);
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed initial access");
  f.assignExisting();
  await page.getByRole("button", { name: "Grant access", exact: true }).click();
  await expect(page.getByRole("button", { name: "Grant access", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Review current version", exact: true }).click();
  await page.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("link", { name: "View existing access", exact: true })).toBeVisible();
  expect(f.writes).toHaveLength(1);
  expect(f.memberships.get(companyIds[0])?.get(managedAccountId())?.member.version).toBe(4);
});

test("account deactivation after review requires new preparation and clears the unusable form", async ({
  page,
}) => {
  const f = await installCompanyMembershipCreationApi(page);
  await openEditor(page, f.editor);
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed initial access");
  const account = f.accounts.get(managedAccountId());
  if (!account) throw new Error("Missing fixture account");
  account.active = false;
  await page.getByRole("button", { name: "Grant access", exact: true }).click();
  await page.getByRole("button", { name: "Review current version", exact: true }).click();
  await page.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "This account cannot receive active company access.",
  );
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  expect(f.memberships.get(companyIds[0])?.size).toBe(0);
});

test("a revised role must be selected again before a new assignment can be saved", async ({
  page,
}) => {
  const f = await installCompanyMembershipCreationApi(page);
  await openEditor(page, f.editor);
  await page.getByRole("button", { name: "Add role", exact: true }).click();
  await selectRole(page);
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed role access");
  const role = f.roles.records.get(companyIds[0])?.[0];
  if (!role) throw new Error("Missing fixture role");
  role.current.version++;
  await page.getByRole("button", { name: "Grant access", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.getByRole("button", { name: "Replace role", exact: true }).click();
  await selectRole(page);
  await expect(page.getByText("Selected version: 22", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Grant access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  expect(f.writes).toHaveLength(2);
  expect(f.writes[0]?.operation).not.toBe(f.writes[1]?.operation);
});

test("changing company while a save is pending prevents its late result entering the new workspace", async ({
  page,
}) => {
  const f = await installCompanyMembershipCreationApi(page);
  await openEditor(page, f.editor);
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed initial access");
  const held = f.holdSave();
  try {
    await page.getByRole("button", { name: "Grant access", exact: true }).click();
    await held.entered;
    await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
    await expect(page.getByRole("button", { name: "Add role", exact: true })).toBeDisabled();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("combobox", { name: "Company", exact: true })).toHaveValue(
      companyIds[1],
    );
  } finally {
    held.release();
  }
  await expect.poll(() => f.memberships.get(companyIds[0])?.has(managedAccountId())).toBe(true);
  await expect(page.getByText("Company access saved.", { exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  expect(f.memberships.get(companyIds[1])?.size).toBe(0);
});

test("leaving a pending preparation cancels it and ignores its late account result", async ({
  page,
}) => {
  const f = await installCompanyMembershipCreationApi(page);
  await page.goto(f.picker);
  const choose = page.getByRole("button", {
    name: "Choose account: Example Colleague 1",
    exact: true,
  });
  await expect(choose).toBeVisible();
  const held = f.holdRead();
  try {
    await choose.click();
    await held.entered;
    const cancelled = page.waitForEvent("requestfailed", {
      predicate: (request) =>
        request.url().endsWith(`/companies/${companyIds[0]}/members/${managedAccountId()}`),
      timeout: 5000,
    });
    await page.getByRole("link", { name: "Overview", exact: true }).click();
    await cancelled;
    await expect(page.getByRole("heading", { name: "Overview", exact: true })).toBeVisible();
  } finally {
    held.release();
  }
  await expect(page.getByRole("heading", { name: "Overview", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Grant access", exact: true })).toHaveCount(0);
  expect(
    f.reads.filter((url) => url.pathname.endsWith(`/identity/accounts/${managedAccountId()}`)),
  ).toHaveLength(0);
});

test("company-only administrators cannot discover outside accounts through direct routes", async ({
  page,
}) => {
  const f = await installCompanyMembershipCreationApi(page, { platform: false });
  await page.goto(`/administration/members?company=${companyIds[0]}`);
  await expect(page.getByRole("heading", { name: "Company accounts", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Add account", exact: true })).toHaveCount(0);
  const reads = f.reads.length;
  await page.goto(f.picker);
  await expect(page.getByRole("alert")).toContainText(
    "This action requires platform administration access.",
  );
  await page.goto(f.editor);
  await expect(page.getByRole("alert")).toContainText(
    "This action requires platform administration access.",
  );
  expect(f.reads).toHaveLength(reads);
  expect(f.writes).toHaveLength(0);
});

test("company replacement in a direct link never acquires the old company's candidate", async ({
  page,
}) => {
  const f = await installCompanyMembershipCreationApi(page);
  await page.goto(`/administration/members/new/${managedAccountId()}?company=${companyIds[1]}`);
  await expect(page).toHaveURL(
    new RegExp(`/administration/members\\?company=${companyIds[0]}$`, "u"),
  );
  await expect(page.getByRole("heading", { name: "Company accounts", exact: true })).toBeVisible();
  expect(f.reads.some((url) => url.pathname.includes(`/companies/${companyIds[1]}/`))).toBe(false);
  expect(f.reads.some((url) => url.pathname.startsWith("/api/v1/identity/accounts"))).toBe(false);
});

test("new company access remains readable in Indonesian dark mode with doubled text", async ({
  page,
}) => {
  const f = await installCompanyMembershipCreationApi(page);
  await openEditor(page, f.editor);
  await page.getByRole("button", { name: "Add role", exact: true }).click();
  await selectRole(page);
  await page.getByLabel("Reason", { exact: true }).fill("Peninjauan akses perusahaan");
  const reads = f.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => {
    const measurements = Array.from(
      document.querySelectorAll<HTMLElement>("main, main *"),
      (element) => ({
        element,
        font: Number.parseFloat(getComputedStyle(element).fontSize),
        line: Number.parseFloat(getComputedStyle(element).lineHeight),
      }),
    );
    for (const { element, font, line } of measurements) {
      element.style.fontSize = `${font * 2}px`;
      if (Number.isFinite(line)) element.style.lineHeight = `${line * 2}px`;
    }
    scrollTo(0, 0);
  });
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue(
    "Peninjauan akses perusahaan",
  );
  await expect(page.getByRole("button", { name: "Berikan akses", exact: true })).toBeEnabled();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-company-account-assignment-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(f.reads).toHaveLength(reads);
});
