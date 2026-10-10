import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { lifecycleTemplateId } from "./lifecycle-api";
import {
  installLifecycleCaseCreationApi,
  lifecycleEmployeeId,
} from "./lifecycle-case-creation-api";

const employee = `/people/employees/${lifecycleEmployeeId()}?company=${companyIds[0]}&asOf=2026-10-01`;
const create = `/people/employees/${lifecycleEmployeeId()}/lifecycle/new?company=${companyIds[0]}&asOf=2026-10-01`;
const picker = (page: Page) => page.getByRole("dialog", { name: "Choose template", exact: true });
async function choose(page: Page, name = "North checklist 1 (TEMPLATE_001)") {
  await page.getByRole("button", { name: "Choose template", exact: true }).click();
  await picker(page)
    .getByRole("button", { name: `Select: ${name}`, exact: true })
    .click();
  await expect(picker(page)).toHaveCount(0);
}

test("starts an employee case after reviewing the selected checklist and calendar dates", async ({
  page,
}) => {
  const api = await installLifecycleCaseCreationApi(page);
  await page.goto(employee);
  await page.getByRole("link", { name: "Start lifecycle case", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Start lifecycle case", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("region", { name: "Employee", exact: true })).toContainText(
    "North employee",
  );
  await expect(page.getByLabel("Target date", { exact: true })).toHaveValue("2026-10-01");
  expect(api.reads).toEqual([]);
  expect(api.caseReads).toEqual([]);
  await expect(page.getByRole("button", { name: "Start case", exact: true })).toBeDisabled();
  await choose(page);
  await expect(page.getByRole("button", { name: "Choose template", exact: true })).toBeFocused();
  const checklist = page.getByRole("table", { name: "Checklist", exact: true });
  await expect(checklist).toContainText("Sep 29, 2026");
  await expect(checklist).toContainText("Oct 2, 2026");
  await page.getByLabel("Target date", { exact: true }).fill("2026-10-03");
  await expect(checklist).toContainText("Oct 1, 2026");
  await page.getByLabel("Reason", { exact: true }).fill("Start employee onboarding");
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-case-creation.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Start case", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Lifecycle case started.");
  expect(api.creations).toHaveLength(1);
  expect(api.creations[0]?.body).toMatchObject({
    employmentId: lifecycleEmployeeId(),
    templateId: lifecycleTemplateId(),
    templateVersion: 2,
    targetDate: "2026-10-03",
    reason: "Start employee onboarding",
    assignees: {},
  });
  expect(api.creations[0]?.operation).not.toBe(api.creations[0]?.body.id);
  expect(api.caseCommits).toBe(1);
  expect(api.caseReads).toEqual([]);
  await page.getByRole("link", { name: "Open case", exact: true }).click();
  await expect(page.getByRole("heading", { name: "North employee", exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "North checklist 1",
  );
  await page.getByRole("button", { name: "View task: Review equipment", exact: true }).click();
  await expect(
    page
      .getByRole("dialog", { name: "Review equipment", exact: true })
      .locator(".app-property-row")
      .filter({ has: page.getByText("Assignee account", { exact: true }) })
      .getByRole("definition"),
  ).toHaveText("None");
  expect(new URL(page.url()).searchParams.has("asOf")).toBe(false);
  expect(api.identity.unhandled).toEqual([]);
});

test("template selection uses explicit bounded pages and does not select inactive definitions", async ({
  page,
}) => {
  const api = await installLifecycleCaseCreationApi(page);
  await page.goto(create);
  await page.getByRole("button", { name: "Choose template", exact: true }).click();
  const table = picker(page).getByRole("table", { name: "Lifecycle templates", exact: true });
  await expect(table.getByRole("row")).toHaveCount(11);
  await expect(
    table.getByRole("button", { name: "Select: North checklist 2 (TEMPLATE_002)", exact: true }),
  ).toHaveCount(0);
  await picker(page).getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  expect(api.reads.at(-1)?.searchParams.get("after")).toBe("TEMPLATE_020");
  await table
    .getByRole("button", { name: "Select: North checklist 21 (TEMPLATE_021)", exact: true })
    .click();
  await expect(page.getByRole("textbox", { name: "Template", exact: true })).toHaveValue(
    "North checklist 21 (TEMPLATE_021)",
  );
  expect(api.creations).toEqual([]);
});

test("a changed template is rejected and a newly selected version keeps the other form values", async ({
  page,
}) => {
  const api = await installLifecycleCaseCreationApi(page);
  await page.goto(create);
  await choose(page);
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed onboarding");
  api.advance();
  await page.getByRole("button", { name: "Start case", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This template has changed.");
  await expect(page.locator("body")).not.toContainText("PRIVATE");
  expect(api.caseCommits).toBe(0);
  await choose(page, "Concurrent template (TEMPLATE_001)");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Reviewed onboarding");
  await page.getByRole("button", { name: "Start case", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Lifecycle case started.");
  expect(api.creations[1]?.body.templateVersion).toBe(3);
  expect(api.creations[1]?.body.id).toBe(api.creations[0]?.body.id);
  expect(api.creations[1]?.operation).not.toBe(api.creations[0]?.operation);
  expect(api.caseCommits).toBe(1);
});

test("uncertain creation retains its original request through MFA and later rejection", async ({
  page,
}) => {
  const api = await installLifecycleCaseCreationApi(page);
  await page.goto(create);
  await choose(page);
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed onboarding");
  api.loseCreation();
  await page.getByRole("button", { name: "Start case", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
  api.identity.expireMfa();
  api.rejectCreation("mfa_required", 403);
  await page.getByRole("button", { name: "Retry original request", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Reviewed onboarding");
  expect(api.creations).toHaveLength(2);
  api.advance();
  api.rejectCreation("stale_template_version");
  await page.getByRole("button", { name: "Retry original request", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This template has changed.");
  await expect(page.getByRole("button", { name: "Choose template", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Retry original request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Lifecycle case started.");
  expect(api.creations).toHaveLength(4);
  for (const write of api.creations) {
    expect(write.operation).toBe(api.creations[0]?.operation);
    expect(write.body).toEqual(api.creations[0]?.body);
  }
  expect(new Set(api.creations.map((write) => write.csrf)).size).toBe(4);
  expect(api.caseCommits).toBe(1);
});

for (const missing of ["people.read", "people.lifecycle.read", "people.lifecycle.manage"]) {
  test(`the creation route requires ${missing} before acquiring employee or template data`, async ({
    page,
  }) => {
    const api = await installLifecycleCaseCreationApi(page, {
      permissions: ["people.read", "people.lifecycle.read", "people.lifecycle.manage"].filter(
        (grant) => grant !== missing,
      ),
    });
    await page.goto(create);
    await expect(page.getByRole("alert")).toContainText("You do not have access");
    await expect(page.getByRole("button", { name: "Choose template", exact: true })).toHaveCount(0);
    expect(api.employeeReads).toEqual([]);
    expect(api.reads).toEqual([]);
    expect(api.creations).toEqual([]);
    if (missing !== "people.read") {
      await page.goto(employee);
      await expect(
        page.getByRole("heading", { name: "North employee", exact: true }),
      ).toBeVisible();
      await expect(
        page.getByRole("link", { name: "Start lifecycle case", exact: true }),
      ).toHaveCount(0);
    }
  });
}

test("empty template pages and unavailable employees require explicit recovery", async ({
  page,
}) => {
  const api = await installLifecycleCaseCreationApi(page, { emptyTemplates: true });
  api.failEmployee("employee_not_found");
  await page.goto(create);
  await expect(page.getByRole("alert")).toContainText("The employee was not found");
  expect(api.reads).toEqual([]);
  api.failEmployee(null);
  await page.getByRole("button", { name: "Retry", exact: true }).click();
  await page.getByRole("button", { name: "Choose template", exact: true }).click();
  await expect(picker(page).getByRole("status")).toHaveText("No active templates on this page.");
  await expect(picker(page).getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  expect(api.creations).toEqual([]);
});

test("a pending start blocks duplicate submission and discards late receipts after company departure", async ({
  page,
}) => {
  const api = await installLifecycleCaseCreationApi(page);
  await page.goto(create);
  await choose(page);
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed onboarding");
  const held = api.holdCreation();
  try {
    await page.getByRole("button", { name: "Start case", exact: true }).click();
    await held.entered;
    await expect(page.getByRole("button", { name: "Start case", exact: true })).toBeDisabled();
    await page.getByRole("link", { name: "Back to employee", exact: true }).click();
    const departure = page.getByRole("dialog", { name: "Leave this page?", exact: true });
    await departure.getByRole("button", { name: "Stay on this page", exact: true }).click();
    await expect(page.getByRole("link", { name: "Back to employee", exact: true })).toBeFocused();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure.getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(
      page.getByRole("table", { name: "Employee directory", exact: true }),
    ).toContainText("South employee");
    held.release();
    await expect.poll(() => api.caseCommits).toBe(1);
    await expect(page.getByRole("main")).not.toContainText("North employee");
    await expect(page.getByRole("link", { name: "Open case", exact: true })).toHaveCount(0);
    expect(api.creations).toHaveLength(1);
    expect(
      api.employeeReads
        .filter((url) => url.pathname.includes(companyIds[1]))
        .every((url) => !url.pathname.includes(lifecycleEmployeeId())),
    ).toBe(true);
  } finally {
    held.release();
  }
});

test("offboarding preview remains editable across theme and locale changes and fits mobile width", async ({
  page,
}) => {
  const api = await installLifecycleCaseCreationApi(page);
  const offboarding = api.templates.get(companyIds[0])?.[1];
  if (!offboarding) throw new Error("Expected offboarding template");
  offboarding.active = true;
  await page.goto(create);
  await page.getByLabel("Reason", { exact: true }).fill("Review employee departure");
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Pilih template", exact: true }).click();
  const choices = page.getByRole("dialog", { name: "Pilih template", exact: true });
  await expect(
    choices.getByRole("table", { name: "Template lifecycle", exact: true }),
  ).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-template-picker-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await choices
    .getByRole("button", { name: "Pilih: North checklist 2 (TEMPLATE_002)", exact: true })
    .click();
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Review employee departure");
  await expect(page.getByRole("region", { name: "Template", exact: true })).toContainText(
    "Offboarding",
  );
  await expect(page.getByRole("table", { name: "Checklist", exact: true })).toContainText(
    "29 Sep 2026",
  );
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-case-creation-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Mulai proses", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Proses lifecycle dimulai.");
  const createdId = api.creations[0]?.body.id ?? "";
  expect(api.cases.get(companyIds[0])?.get(createdId)?.kind).toBe("OFFBOARDING");
});
