import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { installLifecycleCaseActionApi } from "./lifecycle-case-action-api";
import { lifecycleCaseId } from "./lifecycle-cases-api";

const detail = (n = 3) =>
  `/people/lifecycle/cases/${lifecycleCaseId(0, n)}?company=${companyIds[0]}&status=OPEN`;
const panel = (page: Page, title = "Cancel case") =>
  page.getByRole("dialog", { name: title, exact: true });
async function open(page: Page, title = "Cancel case") {
  await page.goto(detail());
  await page.getByRole("button", { name: title, exact: true }).click();
  await expect(panel(page, title)).toBeVisible();
}

test("onboarding completes only after required work and optional resolution and retains the final transition", async ({
  page,
}) => {
  const api = await installLifecycleCaseActionApi(page);
  await page.goto(detail());
  await expect(
    page.getByRole("button", { name: "Complete onboarding", exact: true }),
  ).toBeDisabled();
  for (const title of ["Review access", "Review equipment", "Welcome session"]) {
    await page.getByRole("button", { name: `View task: ${title}`, exact: true }).click();
    const task = panel(page, title);
    if (title === "Welcome session")
      await task.getByLabel("New status", { exact: true }).selectOption("WAIVED");
    await task.getByLabel("Reason", { exact: true }).fill("Checklist item reviewed");
    await task.getByRole("button", { name: "Save task", exact: true }).click();
    await expect(task).toHaveCount(0);
  }
  await page.getByRole("button", { name: "Complete onboarding", exact: true }).click();
  const completion = panel(page, "Complete onboarding");
  await expect(completion).toContainText("North employee 3");
  await completion.getByLabel("Reason", { exact: true }).fill("Onboarding reviewed");
  await completion.getByRole("button", { name: "Complete onboarding", exact: true }).click();
  await expect(completion).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Completed",
  );
  await expect(page.getByRole("button", { name: "Cancel case", exact: true })).toHaveCount(0);
  expect(api.caseChanges[0]).toMatchObject({
    action: "complete-onboarding",
    body: { expectedVersion: 5, reason: "Onboarding reviewed" },
  });
  expect(api.caseCommits).toBe(1);
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await page.getByRole("button", { name: "View change: 6", exact: true }).click();
  await expect(panel(page, "Change details")).toContainText("Onboarding reviewed");
  expect(api.identity.unhandled).toEqual([]);
});

test("cancelling an offboarding case removes its pending work from the active queue and retains history", async ({
  page,
}) => {
  const api = await installLifecycleCaseActionApi(page);
  await page.goto(detail(2));
  await expect(page.getByRole("button", { name: "Complete onboarding", exact: true })).toHaveCount(
    0,
  );
  await page.getByRole("button", { name: "Cancel case", exact: true }).click();
  await panel(page).getByLabel("Reason", { exact: true }).fill("Duplicate departure checklist");
  await panel(page).getByRole("button", { name: "Cancel case", exact: true }).click();
  await expect(panel(page)).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Cancelled",
  );
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await page.getByRole("button", { name: "View change: 3", exact: true }).click();
  await expect(panel(page, "Change details")).toContainText("Duplicate departure checklist");
  await panel(page, "Change details").getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("link", { name: "Assigned tasks", exact: true }).click();
  await expect(page.getByRole("table", { name: "Assigned tasks", exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", {
      name: "View task: Review equipment · North employee 2",
      exact: true,
    }),
  ).toHaveCount(0);
  expect(
    api.records
      .get(companyIds[0])
      ?.find((record) => record.id === lifecycleCaseId(0, 2))
      ?.tasks.every((task) => task.status === "PENDING"),
  ).toBe(true);
  expect(api.caseCommits).toBe(1);
});

for (const permissions of [
  ["people.lifecycle.read"],
  ["people.lifecycle.read", "people.lifecycle.perform"],
  ["people.lifecycle.manage"],
]) {
  test(`${permissions.join(" and ")} cannot independently acquire and manage a company case`, async ({
    page,
  }) => {
    const api = await installLifecycleCaseActionApi(page, { permissions });
    await page.goto(detail());
    if (permissions.includes("people.lifecycle.read"))
      await expect(
        page.getByRole("heading", { name: "North employee 3", exact: true }),
      ).toBeVisible();
    else await expect(page.getByRole("alert")).toContainText("You do not have access");
    await expect(page.getByRole("button", { name: "Cancel case", exact: true })).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "Complete onboarding", exact: true }),
    ).toHaveCount(0);
    expect(api.caseChanges).toEqual([]);
  });
}

test("uncertain cancellation retains its command through MFA and an already-closed reply", async ({
  page,
}) => {
  const api = await installLifecycleCaseActionApi(page);
  await open(page);
  await panel(page).getByLabel("Reason", { exact: true }).fill("Duplicate checklist");
  api.loseCaseChange();
  await panel(page).getByRole("button", { name: "Cancel case", exact: true }).click();
  await expect(panel(page).getByRole("status")).toContainText("The result is unconfirmed.");
  api.identity.expireMfa();
  api.rejectCaseChange("mfa_required", 403);
  await panel(page).getByRole("button", { name: "Retry original action", exact: true }).click();
  await panel(page)
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = panel(page, "Account verification");
  await expect(panel(page)).not.toBeVisible();
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(panel(page).getByLabel("Reason", { exact: true })).toHaveValue(
    "Duplicate checklist",
  );
  api.rejectCaseChange("lifecycle_case_not_open");
  await panel(page).getByRole("button", { name: "Retry original action", exact: true }).click();
  await expect(panel(page).getByRole("alert")).toContainText("This case is no longer open.");
  await expect(panel(page).getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
  await panel(page).getByRole("button", { name: "Retry original action", exact: true }).click();
  await expect(panel(page)).toHaveCount(0);
  expect(api.caseChanges).toHaveLength(4);
  for (const write of api.caseChanges) {
    expect(write.body).toEqual(api.caseChanges[0]?.body);
    expect(write.operation).toBe(api.caseChanges[0]?.operation);
  }
  expect(new Set(api.caseChanges.map((write) => write.csrf)).size).toBe(4);
  expect(api.caseCommits).toBe(1);
});

test("a concurrent task change requires a protected reload and a new case version", async ({
  page,
}) => {
  const api = await installLifecycleCaseActionApi(page);
  api.resolveTasks();
  await open(page, "Complete onboarding");
  const completion = panel(page, "Complete onboarding");
  await completion.getByLabel("Reason", { exact: true }).fill("Onboarding reviewed");
  api.reopenEquipment();
  await completion.getByRole("button", { name: "Complete onboarding", exact: true }).click();
  await expect(completion.getByRole("alert")).toContainText("This record has changed.");
  await expect(
    completion.getByRole("button", { name: "Complete onboarding", exact: true }),
  ).toBeDisabled();
  await completion.getByRole("button", { name: "Reload case", exact: true }).click();
  const departure = panel(page, "Leave this page?");
  await departure.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(completion.getByRole("button", { name: "Reload case", exact: true })).toBeFocused();
  await completion.getByRole("button", { name: "Reload case", exact: true }).click();
  await departure.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(completion).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Complete onboarding", exact: true }),
  ).toBeDisabled();
  api.resolveTasks();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page.getByRole("button", { name: "Complete onboarding", exact: true }).click();
  await expect(completion.getByLabel("Reason", { exact: true })).toHaveValue("");
  await completion.getByLabel("Reason", { exact: true }).fill("Equipment checked again");
  await completion.getByRole("button", { name: "Complete onboarding", exact: true }).click();
  await expect(completion).toHaveCount(0);
  expect(api.caseChanges.map((write) => write.body.expectedVersion)).toEqual([5, 7]);
  expect(api.caseChanges[1]?.operation).not.toBe(api.caseChanges[0]?.operation);
  expect(api.caseCommits).toBe(1);
});

test("case actions validate the reason locally and remain editable after definite validation rejection", async ({
  page,
}) => {
  const api = await installLifecycleCaseActionApi(page);
  await open(page);
  await panel(page).getByRole("button", { name: "Cancel case", exact: true }).click();
  await expect(panel(page).getByRole("alert")).toContainText("provide a reason");
  expect(api.caseChanges).toEqual([]);
  await panel(page).getByLabel("Reason", { exact: true }).fill("Duplicate checklist");
  api.rejectCaseChange("invalid_lifecycle_change", 422);
  await panel(page).getByRole("button", { name: "Cancel case", exact: true }).click();
  await expect(panel(page).getByRole("alert")).toContainText("provide a reason");
  await expect(panel(page).getByLabel("Reason", { exact: true })).not.toHaveAttribute(
    "readonly",
    "",
  );
  await expect(panel(page)).not.toContainText("PRIVATE");
  await panel(page).getByLabel("Reason", { exact: true }).fill("Duplicate checklist confirmed");
  await panel(page).getByRole("button", { name: "Cancel case", exact: true }).click();
  await expect(panel(page)).toHaveCount(0);
  expect(api.caseChanges[1]?.operation).not.toBe(api.caseChanges[0]?.operation);
  expect(api.caseCommits).toBe(1);
});

test("departure cancels owned work and cannot restore a late case receipt into another company", async ({
  page,
}) => {
  const api = await installLifecycleCaseActionApi(page);
  await open(page);
  await panel(page).getByLabel("Reason", { exact: true }).fill("Duplicate checklist");
  const held = api.holdCaseChange();
  try {
    await panel(page).getByRole("button", { name: "Cancel case", exact: true }).click();
    await held.entered;
    await expect(
      panel(page).getByRole("button", { name: "Cancel case", exact: true }),
    ).toBeDisabled();
    await panel(page).getByRole("button", { name: "Close", exact: true }).click();
    const departure = panel(page, "Leave this page?");
    await departure.getByRole("button", { name: "Stay on this page", exact: true }).click();
    await expect(panel(page).getByRole("button", { name: "Close", exact: true })).toBeFocused();
    await panel(page).getByRole("button", { name: "Close", exact: true }).click();
    await departure.getByRole("button", { name: "Leave page", exact: true }).click();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Cases", exact: true })).toContainText(
      "South employee 1",
    );
    held.release();
    await expect.poll(() => api.caseCommits).toBe(1);
    await expect(page.getByRole("main")).not.toContainText("North employee");
    expect(api.caseChanges).toHaveLength(1);
  } finally {
    held.release();
  }
});

for (const status of ["COMPLETED", "CANCELLED"] as const) {
  test(`${status} cases retain details without exposing another closing action`, async ({
    page,
  }) => {
    const api = await installLifecycleCaseActionApi(page);
    const record = api.records
      .get(companyIds[0])
      ?.find((item) => item.id === lifecycleCaseId(0, 3));
    if (!record) throw new Error("Expected case");
    record.status = status;
    await page.goto(detail());
    await expect(
      page.getByRole("heading", { name: "North employee 3", exact: true }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancel case", exact: true })).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "Complete onboarding", exact: true }),
    ).toHaveCount(0);
    expect(api.caseChanges).toEqual([]);
  });
}

test("case review and completion fit dark Indonesian mobile layouts", async ({ page }) => {
  const api = await installLifecycleCaseActionApi(page);
  api.resolveTasks();
  await page.goto(detail());
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Selesaikan onboarding", exact: true }).click();
  const completion = panel(page, "Selesaikan onboarding");
  await completion.getByLabel("Alasan", { exact: true }).fill("Checklist reviewed");
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-case-completion-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await completion.getByRole("button", { name: "Selesaikan onboarding", exact: true }).click();
  await expect(completion).toHaveCount(0);
  expect(api.caseCommits).toBe(1);
});
