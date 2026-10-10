import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { installLifecycleApi, lifecycleTemplateId } from "./lifecycle-api";

const catalog = `/people/lifecycle/templates?company=${companyIds[0]}`;
const detail = `/people/lifecycle/templates/${lifecycleTemplateId()}?company=${companyIds[0]}`;
const editor = `/people/lifecycle/templates/${lifecycleTemplateId()}/edit?company=${companyIds[0]}`;
const save = (page: Page) => page.getByRole("button", { name: "Save template", exact: true });
const task = (page: Page, index: number) =>
  page.getByRole("group", { name: `Task ${index}`, exact: true });
async function openEditor(page: Page) {
  await page.goto(editor);
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("North checklist 1");
  await page.getByLabel("Reason", { exact: true }).fill("Checklist review");
}

test("the catalog uses bounded cursor pages and restores company-scoped history", async ({
  page,
}) => {
  const api = await installLifecycleApi(page, { permissions: ["people.lifecycle.read"] });
  await page.goto("/people/lifecycle/templates");
  await expect(page).toHaveURL(new RegExp(`company=${companyIds[0]}$`, "u"));
  const table = page.getByRole("table", { name: "Templates", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  await expect(page.getByRole("link", { name: "Create template", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await expect(table).toContainText("North checklist 21");
  await expect(page).toHaveURL(/after=TEMPLATE_020$/u);
  await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  await page.goBack();
  await expect(table.getByRole("row")).toHaveCount(21);
  await table
    .getByRole("button", { name: "View template: North checklist 1 (TEMPLATE_001)", exact: true })
    .click();
  await expect(page.getByRole("heading", { name: "North checklist 1", exact: true })).toBeVisible();
  await expect(page.getByRole("table", { name: "Checklist", exact: true })).toContainText(
    "Review equipment",
  );
  await expect(page.getByRole("link", { name: "Edit template", exact: true })).toHaveCount(0);
  expect(api.identity.unhandled).toEqual([]);
});

test("task rows retain the correct values after removal and across locale, theme and layout changes", async ({
  page,
}) => {
  const api = await installLifecycleApi(page);
  await page.goto(detail);
  await page.getByRole("link", { name: "Edit template", exact: true }).click();
  await expect(page.getByLabel("Code", { exact: true })).toHaveAttribute("readonly", "");
  await expect(page.getByLabel("Type", { exact: true })).toHaveAttribute("readonly", "");
  await task(page, 1).getByRole("button", { name: "Remove task", exact: true }).click();
  await expect(task(page, 1).getByLabel("Task title", { exact: true })).toHaveValue(
    "Welcome session",
  );
  await expect(
    task(page, 1).getByRole("checkbox", { name: "Required", exact: true }),
  ).not.toBeChecked();
  await page.getByRole("button", { name: "Add task", exact: true }).click();
  await task(page, 2).getByLabel("Key", { exact: true }).fill("access");
  await task(page, 2).getByLabel("Task title", { exact: true }).fill("Review access");
  await task(page, 2).getByLabel("Due offset (days)", { exact: true }).fill("-3");
  await page.getByRole("checkbox", { name: "Active", exact: true }).uncheck();
  await page.getByLabel("Reason", { exact: true }).fill("Checklist update");
  const reads = api.reads.length;
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(
    page
      .getByRole("group", { name: "Tugas 2", exact: true })
      .getByLabel("Judul tugas", { exact: true }),
  ).toHaveValue("Review access");
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Simpan template", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Template tersimpan.");
  expect(api.writes[0]?.body).toEqual({
    expectedVersion: 2,
    code: "TEMPLATE_001",
    name: "North checklist 1",
    kind: "ONBOARDING",
    active: false,
    reason: "Checklist update",
    tasks: [
      { key: "welcome", title: "Welcome session", required: false, dueDays: 1 },
      { key: "access", title: "Review access", required: true, dueDays: -3 },
    ],
  });
  await page.getByRole("link", { name: "Lihat template", exact: true }).click();
  await expect(page.getByRole("table", { name: "Checklist", exact: true })).toContainText(
    "Review access",
  );
  await expect(page.getByRole("table", { name: "Checklist", exact: true })).not.toContainText(
    "Review equipment",
  );
  expect(api.commits).toBe(1);
  expect(api.identity.unhandled).toEqual([]);
});

test("manage-only creation does not acquire template or employee records", async ({ page }) => {
  const api = await installLifecycleApi(page, {
    permissions: ["people.lifecycle.manage"],
    empty: true,
  });
  await page.goto("/");
  await page.getByRole("link", { name: "Create template", exact: true }).click();
  await page.getByLabel("Code", { exact: true }).fill(" offboard ");
  await page.getByLabel("Name", { exact: true }).fill("Standard offboarding");
  await page.getByLabel("Type", { exact: true }).selectOption("OFFBOARDING");
  await task(page, 1).getByLabel("Key", { exact: true }).fill("equipment");
  await task(page, 1).getByLabel("Task title", { exact: true }).fill("Return equipment");
  await page.getByLabel("Reason", { exact: true }).fill("New checklist");
  await save(page).click();
  await expect(page.getByRole("status")).toContainText("Template saved.");
  expect(api.reads).toEqual([]);
  expect(api.writes[0]?.body).toMatchObject({
    code: "OFFBOARD",
    kind: "OFFBOARDING",
    expectedVersion: null,
  });
  await expect(page.getByRole("link", { name: "View template", exact: true })).toHaveCount(0);
  expect(api.identity.unhandled).toEqual([]);
});

test("invalid task keys, duplicate keys and empty due offsets are rejected before the request", async ({
  page,
}) => {
  const api = await installLifecycleApi(page);
  await openEditor(page);
  for (const key of ["UPPER", "equipment"]) {
    await task(page, 2).getByLabel("Key", { exact: true }).fill(key);
    await save(page).click();
    await expect(page.getByRole("alert")).toContainText("Check the template");
  }
  await task(page, 2).getByLabel("Key", { exact: true }).fill("welcome");
  await task(page, 1).getByLabel("Due offset (days)", { exact: true }).fill("");
  await save(page).click();
  await expect(page.getByRole("alert")).toContainText("Check the template");
  expect(api.writes).toEqual([]);
  await task(page, 1).getByLabel("Due offset (days)", { exact: true }).fill("0");
  await save(page).click();
  await expect(page.getByRole("status")).toContainText("Template saved.");
  expect(api.commits).toBe(1);
});

test("uncertain saves retain the original payload through MFA and later conflict replies", async ({
  page,
}) => {
  const api = await installLifecycleApi(page);
  await openEditor(page);
  api.loseNext();
  await save(page).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed.");
  await expect(task(page, 1).getByLabel("Task title", { exact: true })).toHaveAttribute(
    "readonly",
    "",
  );
  await expect(page.getByRole("button", { name: "Add task", exact: true })).toBeDisabled();
  api.identity.expireMfa();
  api.rejectNext("mfa_required", 403);
  await page.getByRole("button", { name: "Retry original save", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  expect(api.writes).toHaveLength(2);
  api.rejectNext("stale_version");
  await page.getByRole("button", { name: "Retry original save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed.");
  await page.getByRole("button", { name: "Retry original save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Template saved.");
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) {
    expect(write.operation).toBe(api.writes[0]?.operation);
    expect(write.body).toEqual(api.writes[0]?.body);
  }
  expect(new Set(api.writes.map((write) => write.csrf)).size).toBe(4);
  expect(api.commits).toBe(1);
  expect(api.identity.unhandled).toEqual([]);
});

test("a definite conflict reloads a fresh version only after the user accepts losing edits", async ({
  page,
}) => {
  const api = await installLifecycleApi(page);
  await openEditor(page);
  api.advance();
  await save(page).click();
  await expect(page.getByRole("alert")).toContainText("changed");
  await expect(save(page)).toBeDisabled();
  await page.getByRole("button", { name: "Reload template", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Leave this page?", exact: true });
  await dialog.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Checklist review");
  await page.getByRole("button", { name: "Reload template", exact: true }).click();
  await dialog.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Concurrent template");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Review current version");
  await save(page).click();
  await expect(page.getByRole("status")).toContainText("Template saved.");
  expect(api.writes[1]?.body.expectedVersion).toBe(3);
  expect(api.commits).toBe(1);
});

test("dirty checklist departures protect task edits and a company switch disposes the former editor", async ({
  page,
}) => {
  const api = await installLifecycleApi(page);
  await openEditor(page);
  await task(page, 1).getByLabel("Task title", { exact: true }).fill("Private draft");
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  const dialog = page.getByRole("dialog", { name: "Leave this page?", exact: true });
  await dialog.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(task(page, 1).getByLabel("Task title", { exact: true })).toHaveValue(
    "Private draft",
  );
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await dialog.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("main")).toContainText("South Company");
  await expect(page.getByRole("main")).not.toContainText("North checklist");
  await expect(page.getByRole("main")).not.toContainText("Private draft");
  expect(api.writes).toEqual([]);
});

test("a pending save accepts one command and retains fields while navigation is deferred", async ({
  page,
}) => {
  const api = await installLifecycleApi(page);
  await openEditor(page);
  const held = api.holdNextWrite();
  try {
    await save(page).click();
    await held.entered;
    await expect(save(page)).toBeDisabled();
    await page.getByRole("link", { name: "Overview", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "Leave this page?", exact: true });
    await dialog.getByRole("button", { name: "Stay on this page", exact: true }).click();
    expect(api.writes).toHaveLength(1);
    held.release();
    await expect(page.getByRole("status")).toContainText("Template saved.");
  } finally {
    held.release();
  }
});

test("a late read cannot restore the previous company's template", async ({ page }) => {
  const api = await installLifecycleApi(page);
  await page.goto(detail);
  await expect(page.getByRole("heading", { name: "North checklist 1", exact: true })).toBeVisible();
  const held = api.holdNextRead();
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("main")).toContainText("South Company");
    held.release();
    await expect(page.getByRole("main")).not.toContainText("North checklist");
    expect(api.reads.at(-1)?.pathname).toContain(companyIds[1]);
  } finally {
    held.release();
  }
});

test("direct links still require feature access and reject malformed identifiers without I/O", async ({
  page,
}) => {
  const api = await installLifecycleApi(page, { permissions: ["people.lifecycle.perform"] });
  await page.goto(detail);
  await expect(page.getByRole("alert")).toContainText("do not have access");
  await page.goto(editor);
  await expect(page.getByRole("alert")).toContainText("do not have access");
  expect(api.reads).toEqual([]);
  expect(api.writes).toEqual([]);
  api.identity.setPermissions(["people.lifecycle.read"]);
  await page.goto(`/people/lifecycle/templates/invalid?company=${companyIds[0]}`);
  await expect(page.getByRole("alert")).toContainText("unavailable in the selected company");
  expect(api.reads).toEqual([]);
  expect(api.identity.unhandled).toEqual([]);
});

test("a revoked template read clears previous data and keeps internal diagnostics out of the page", async ({
  page,
}) => {
  const api = await installLifecycleApi(page);
  await page.goto(catalog);
  await expect(page.getByRole("table", { name: "Templates", exact: true })).toBeVisible();
  api.failReads("access_denied");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("do not have access");
  await expect(page.getByRole("table", { name: "Templates", exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE LIFECYCLE DETAILS");
  api.failReads(null);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("table", { name: "Templates", exact: true })).toBeVisible();
});
