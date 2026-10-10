import { expect, test } from "@playwright/test";
import { leavePolicyId } from "./fixtures/leave-policies";
import { companyIds } from "./identity-api";
import { installLeavePoliciesApi } from "./leave-policies-api";

const query = `company=${companyIds[0]}`;
const directory = `/leave/policies?${query}`;
const detail = `/leave/policies/${leavePolicyId()}?${query}`;

test("policy catalog retains company, filters and cursor through detail and browser navigation", async ({
  page,
}) => {
  const api = await installLeavePoliciesApi(page);
  await page.goto(directory);
  const table = page.getByRole("table", { name: "Leave policies", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  const future = table.getByRole("row").filter({ hasText: "North leave 01" });
  await expect(future).toContainText("2027");
  await expect(future).toContainText("Disabled");
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(4);
  await table.getByRole("button", { name: "View: North leave 23", exact: true }).click();
  await expect(page.getByRole("region", { name: "Policy terms", exact: true })).toContainText(
    "North leave 23",
  );
  await page.getByRole("link", { name: "Back to policies", exact: true }).click();
  await expect(page).toHaveURL(`${directory}&after=LEAVE020`);
  await expect(table.getByRole("row")).toHaveCount(4);
  const before = api.reads.length;
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("false");
  expect(api.reads).toHaveLength(before);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await expect(page).toHaveURL(`${directory}&active=false`);
  expect(api.reads.at(-1)?.searchParams.get("active")).toBe("false");
  expect(api.reads.at(-1)?.searchParams.has("after")).toBe(false);
  await page.goBack();
  await expect(table.getByRole("row")).toHaveCount(4);
  await expect(page.getByRole("combobox", { name: "Status", exact: true })).toHaveValue("");
  await page.goForward();
  await expect(table.getByRole("row")).toHaveCount(2);
});

test("older policy selection shows immutable terms without replacing the current head or rereading", async ({
  page,
}) => {
  const api = await installLeavePoliciesApi(page);
  await page.goto(detail);
  const history = page.getByRole("table", { name: "Policy history", exact: true });
  const current = page.getByRole("region", { name: "Latest saved revision", exact: true });
  await expect(history.getByRole("row")).toHaveCount(21);
  await expect(current).toContainText("23");
  await expect(current).toContainText("2027");
  await page.getByRole("button", { name: "Older revisions", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(5);
  await expect(page).toHaveURL(`${detail}&historyAfter=4`);
  const reads = api.reads.length;
  await page.getByRole("button", { name: "View: Revision 3", exact: true }).click();
  const revision = page.getByRole("region", { name: "Revision 3", exact: true });
  await expect(revision).toBeFocused();
  await expect(revision).toContainText("North leave 01 / revision 3");
  await expect(revision).toContainText("Policy review 3");
  await expect(current).toContainText("23");
  await expect(current).toContainText("2027");
  expect(api.reads).toHaveLength(reads);
  await page.goBack();
  await expect(history.getByRole("row")).toHaveCount(21);
  await expect(revision).toHaveCount(0);
  await page.goForward();
  await expect(history.getByRole("row")).toHaveCount(5);
  await page.getByRole("button", { name: "Latest revisions", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(21);
});

for (const kind of ["list", "details"] as const)
  test(`company replacement cancels a pending policy ${kind} and drops its cursor`, async ({
    page,
  }) => {
    const api = await installLeavePoliciesApi(page);
    const held = api.hold(kind);
    try {
      await page.goto(kind === "list" ? `${directory}&after=LEAVE020` : `${detail}&historyAfter=4`);
      await held.entered;
      await page
        .getByRole("combobox", { name: "Company", exact: true })
        .selectOption(companyIds[1]);
      await expect(page.getByRole("table", { name: "Leave policies", exact: true })).toContainText(
        "South leave 01",
      );
      held.release();
      await expect(page).toHaveURL(`/leave/policies?company=${companyIds[1]}`);
      await expect(page.getByRole("main")).not.toContainText("North leave");
    } finally {
      held.release();
    }
  });

test("company replacement removes selected policy evidence", async ({ page }) => {
  await installLeavePoliciesApi(page);
  await page.goto(detail);
  await page.getByRole("button", { name: "View: Revision 23", exact: true }).click();
  await expect(page.getByRole("region", { name: "Revision 23", exact: true })).toBeVisible();
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(page.getByRole("table", { name: "Leave policies", exact: true })).toContainText(
    "South leave 01",
  );
  await expect(page.getByRole("region", { name: "Revision 23", exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("Policy review 23");
});

test("leave readers cannot load administrative policy evidence or discover its navigation", async ({
  page,
}) => {
  const api = await installLeavePoliciesApi(page, ["leave.read"]);
  await page.goto(detail);
  await expect(page.getByRole("alert")).toContainText("You do not have access to this resource");
  await expect(page.getByRole("link", { name: "Leave policies", exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(0);
  await page.goto(directory);
  await expect(page.getByRole("alert")).toContainText("You do not have access to this resource");
  expect(api.reads).toHaveLength(0);
});

test("invalid policy cursors stay local and a foreign identity remains unavailable", async ({
  page,
}) => {
  const api = await installLeavePoliciesApi(page);
  await page.goto(`${directory}&active=UNKNOWN`);
  await expect(page.getByRole("alert")).toContainText("This page link is invalid");
  await page.goto(`${detail}&historyAfter=9007199254740992`);
  await expect(page.getByRole("alert")).toContainText("This page link is invalid");
  expect(api.reads).toHaveLength(0);
  await page.goto(`/leave/policies/${leavePolicyId(1)}?${query}`);
  await expect(page.getByRole("alert")).toContainText("This leave policy is no longer available");
  await expect(page.getByRole("region", { name: "Policy terms", exact: true })).toHaveCount(0);
});

for (const path of [directory, detail])
  test(`invalid policy data is not rendered at ${path}`, async ({ page }) => {
    const api = await installLeavePoliciesApi(page);
    api.malformed();
    await page.goto(path);
    await expect(page.getByRole("alert")).toContainText("response");
    await expect(page.getByRole("main")).not.toContainText("North leave");
  });

test("an access failure clears policy evidence and only an explicit refresh recovers it", async ({
  page,
}) => {
  const api = await installLeavePoliciesApi(page);
  await page.goto(detail);
  await page.getByRole("button", { name: "View: Revision 23", exact: true }).click();
  api.failNext("leave_type_not_found");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This leave policy is no longer available");
  await expect(page.getByRole("main")).not.toContainText("North leave");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE TECHNICAL");
  await expect(page.getByRole("region", { name: "Revision 23", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Latest saved revision", exact: true }),
  ).toBeVisible();
});

test("MFA suspension removes policy evidence and renewal does not silently refetch it", async ({
  page,
}) => {
  const api = await installLeavePoliciesApi(page);
  await page.goto(detail);
  await page.getByRole("button", { name: "View: Revision 23", exact: true }).click();
  api.identity.expireMfa();
  api.failNext("mfa_required");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Revision 23", exact: true })).toHaveCount(0);
  const reads = api.reads.length;
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Latest saved revision", exact: true }),
  ).toBeVisible();
});

test("policy review supports localized dark mobile layout without restarting its read", async ({
  page,
}) => {
  const api = await installLeavePoliciesApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(detail);
  await page.getByRole("button", { name: "View: Revision 23", exact: true }).click();
  const reads = api.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("button", { name: "Muat ulang", exact: true })).toHaveCSS(
    "color",
    "rgb(255, 255, 255)",
  );
  await expect(page.getByRole("heading", { name: "Detail kebijakan", exact: true })).toBeVisible();
  const revision = page.getByRole("region", { name: "Revisi 23", exact: true });
  await expect(revision).toContainText("Bulanan");
  await expect(revision).toContainText("1,5");
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-leave-policy-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});
