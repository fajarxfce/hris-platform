import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { lifecycleCaseId } from "./lifecycle-cases-api";
import { installLifecycleTaskApi } from "./lifecycle-task-api";

const detail = `/people/lifecycle/cases/${lifecycleCaseId()}?company=${companyIds[0]}&status=OPEN`;
const queue = `/people/lifecycle/tasks?company=${companyIds[0]}`;
const taskPanel = (page: Page) =>
  page.getByRole("dialog", { name: "Review equipment", exact: true });
async function openTask(page: Page) {
  await page.goto(detail);
  await page.getByRole("button", { name: "View task: Review equipment", exact: true }).click();
  await expect(taskPanel(page)).toBeVisible();
}

test("a performer completes an assigned task without acquiring company cases or private profiles", async ({
  page,
}) => {
  const api = await installLifecycleTaskApi(page, { permissions: ["people.lifecycle.perform"] });
  await page.goto(queue);
  const table = page.getByRole("table", { name: "Assigned tasks", exact: true });
  const task = table.getByRole("button", {
    name: "View task: Review equipment · North employee 1",
    exact: true,
  });
  await task.click();
  const panel = taskPanel(page);
  await expect(panel.getByLabel("New status", { exact: true })).toHaveValue("DONE");
  await expect(panel.locator('option[value="WAIVED"]')).toHaveCount(0);
  await panel.getByLabel("Reason", { exact: true }).fill("  Equipment received  ");
  await panel.getByRole("button", { name: "Save task", exact: true }).click();
  await expect(panel).toHaveCount(0);
  await expect(task).toHaveCount(0);
  expect(api.writes[0]?.body).toEqual({
    expectedVersion: 50,
    status: "DONE",
    reason: "Equipment received",
  });
  expect(api.commits).toBe(1);
  expect(api.reads.every((url) => url.pathname.endsWith("/tasks/assigned"))).toBe(true);
  expect(api.identity.unhandled).toEqual([]);
});

test("a manager waives only an optional task and can reopen it with immutable history", async ({
  page,
}) => {
  const api = await installLifecycleTaskApi(page, {
    permissions: ["people.lifecycle.read", "people.lifecycle.manage"],
  });
  await openTask(page);
  await expect(taskPanel(page).locator('option[value="WAIVED"]')).toHaveCount(0);
  await taskPanel(page).getByRole("button", { name: "Close", exact: true }).click();
  const table = page.getByRole("table", { name: "Checklist", exact: true });
  await table.getByRole("button", { name: "View task: Welcome session", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Welcome session", exact: true });
  await panel.getByLabel("New status", { exact: true }).selectOption("WAIVED");
  await panel.getByLabel("Reason", { exact: true }).fill("Session already completed externally");
  await panel.getByRole("button", { name: "Save task", exact: true }).click();
  await expect(panel).toHaveCount(0);
  await expect(table.getByRole("row").filter({ hasText: "Welcome session" })).toContainText(
    "Waived",
  );
  await table.getByRole("button", { name: "View task: Welcome session", exact: true }).click();
  await panel.getByLabel("New status", { exact: true }).selectOption("PENDING");
  await panel.getByLabel("Reason", { exact: true }).fill("Repeat session for updated policy");
  await panel.getByRole("button", { name: "Save task", exact: true }).click();
  await expect(panel).toHaveCount(0);
  await expect(table.getByRole("row").filter({ hasText: "Welcome session" })).toContainText(
    "Pending",
  );
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await page
    .getByRole("group", { name: "History pages", exact: true })
    .getByRole("button", { name: "Next page", exact: true })
    .click();
  await page.getByRole("button", { name: "View change: 51", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Change details", exact: true })).toContainText(
    "Session already completed externally",
  );
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([50, 51]);
  expect(api.commits).toBe(2);
  expect(api.identity.unhandled).toEqual([]);
});

for (const mode of ["read-only", "another assignee"] as const) {
  test(`${mode} tasks do not expose status editing`, async ({ page }) => {
    const api = await installLifecycleTaskApi(page, {
      permissions:
        mode === "read-only"
          ? ["people.lifecycle.read"]
          : ["people.lifecycle.read", "people.lifecycle.perform"],
    });
    if (mode === "another assignee") {
      const task = api.records
        .get(companyIds[0])?.[0]
        ?.tasks.find((item) => item.key === "equipment");
      if (!task) throw new Error("Expected task");
      task.assigneeId = "20000000-0000-4000-8000-000000000002";
    }
    await openTask(page);
    await expect(taskPanel(page)).toContainText("North employee 1");
    await expect(
      taskPanel(page).getByRole("button", { name: "Save task", exact: true }),
    ).toHaveCount(0);
    await expect(taskPanel(page).getByLabel("New status", { exact: true })).toHaveCount(0);
    expect(api.writes).toEqual([]);
  });
}

test("an uncertain task change survives verification and conflicting replies until its original receipt is recovered", async ({
  page,
}) => {
  const api = await installLifecycleTaskApi(page);
  await openTask(page);
  const panel = taskPanel(page);
  await panel.getByLabel("Reason", { exact: true }).fill("Equipment received");
  api.loseNext();
  await panel.getByRole("button", { name: "Save task", exact: true }).click();
  await expect(panel.getByRole("status")).toContainText("The save could not be confirmed.");
  await expect(panel.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
  await expect(panel.getByLabel("New status", { exact: true })).toBeDisabled();
  api.identity.expireMfa();
  api.rejectNext("mfa_required", 403);
  await panel.getByRole("button", { name: "Retry save", exact: true }).click();
  await panel
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await expect(panel).not.toBeVisible();
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(panel.getByLabel("Reason", { exact: true })).toHaveValue("Equipment received");
  expect(api.writes).toHaveLength(2);
  api.rejectNext("stale_version");
  await panel.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(panel.getByRole("status")).toContainText("The save could not be confirmed.");
  await panel.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(panel).toHaveCount(0);
  await expect(
    page
      .getByRole("table", { name: "Checklist", exact: true })
      .getByRole("row")
      .filter({ hasText: "Review equipment" }),
  ).toContainText("Done");
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) {
    expect(write.operation).toBe(api.writes[0]?.operation);
    expect(write.body).toEqual(api.writes[0]?.body);
  }
  expect(new Set(api.writes.map((write) => write.csrf)).size).toBe(4);
  expect(api.commits).toBe(1);
  expect(api.identity.unhandled).toEqual([]);
});

test("a definite conflict requires an explicit departure before loading a current task version", async ({
  page,
}) => {
  const api = await installLifecycleTaskApi(page);
  await openTask(page);
  const panel = taskPanel(page);
  await panel.getByLabel("Reason", { exact: true }).fill("Original review");
  api.advance();
  await panel.getByRole("button", { name: "Save task", exact: true }).click();
  await expect(panel.getByRole("alert")).toContainText("changed");
  await expect(panel.getByRole("button", { name: "Save task", exact: true })).toBeDisabled();
  await panel.getByRole("button", { name: "Reload task list", exact: true }).click();
  const departure = page.getByRole("dialog", { name: "Leave this page?", exact: true });
  await departure.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(panel.getByLabel("Reason", { exact: true })).toHaveValue("Original review");
  await panel.getByRole("button", { name: "Reload task list", exact: true }).click();
  await departure.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(panel).toHaveCount(0);
  await page.getByRole("button", { name: "View task: Review equipment", exact: true }).click();
  await expect(panel.getByLabel("Reason", { exact: true })).toHaveValue("");
  await panel.getByLabel("Reason", { exact: true }).fill("Review current checklist");
  await panel.getByRole("button", { name: "Save task", exact: true }).click();
  await expect(panel).toHaveCount(0);
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([50, 51]);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
  expect(api.commits).toBe(1);
});

test("a required reason is validated before transport and can be corrected in the same panel", async ({
  page,
}) => {
  const api = await installLifecycleTaskApi(page);
  await openTask(page);
  const panel = taskPanel(page);
  await panel.getByRole("button", { name: "Save task", exact: true }).click();
  await expect(panel.getByRole("alert")).toContainText("provide a reason");
  expect(api.writes).toEqual([]);
  await panel.getByLabel("Reason", { exact: true }).fill("Equipment received");
  await panel.getByRole("button", { name: "Save task", exact: true }).click();
  await expect(panel).toHaveCount(0);
  expect(api.commits).toBe(1);
});

test("closing a pending task requires consent and its late completion cannot enter another company", async ({
  page,
}) => {
  const api = await installLifecycleTaskApi(page, { permissions: ["people.lifecycle.perform"] });
  await page.goto(queue);
  await page
    .getByRole("button", { name: "View task: Review equipment · North employee 1", exact: true })
    .click();
  const panel = taskPanel(page);
  await panel.getByLabel("Reason", { exact: true }).fill("Equipment received");
  const held = api.holdNextWrite();
  try {
    await panel.getByRole("button", { name: "Save task", exact: true }).click();
    await held.entered;
    await expect(panel.getByRole("button", { name: "Save task", exact: true })).toBeDisabled();
    await panel.getByRole("button", { name: "Close", exact: true }).click();
    const departure = page.getByRole("dialog", { name: "Leave this page?", exact: true });
    await departure.getByRole("button", { name: "Stay on this page", exact: true }).click();
    expect(api.writes).toHaveLength(1);
    await panel.getByRole("button", { name: "Close", exact: true }).click();
    await departure.getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(panel).toHaveCount(0);
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Assigned tasks", exact: true })).toContainText(
      "South employee 1",
    );
    held.release();
    await expect.poll(() => api.commits).toBe(1);
    await expect(page.getByRole("main")).not.toContainText("North employee");
    await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}$`, "u"));
    expect(api.writes).toHaveLength(1);
    expect(api.identity.unhandled).toEqual([]);
  } finally {
    held.release();
  }
});

test("live permission loss removes the task panel and former employee data", async ({ page }) => {
  const api = await installLifecycleTaskApi(page);
  await openTask(page);
  await taskPanel(page).getByLabel("Reason", { exact: true }).fill("Private review");
  api.identity.setPermissions([]);
  api.rejectNext("company_access_denied", 403);
  await taskPanel(page).getByRole("button", { name: "Save task", exact: true }).click();
  await expect(taskPanel(page)).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("North employee");
  await expect(page.getByRole("main")).not.toContainText("Private review");
  await expect(page.locator("body")).not.toContainText("PRIVATE TASK DETAILS");
  expect(api.commits).toBe(0);
});

test("task editing fits mobile width and uses the selected language and theme", async ({
  page,
}) => {
  await installLifecycleTaskApi(page, {
    permissions: ["people.lifecycle.read", "people.lifecycle.manage"],
  });
  await openTask(page);
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-task-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await taskPanel(page).getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Lihat tugas: Review equipment", exact: true }).click();
  const panel = taskPanel(page);
  await expect(panel.getByLabel("Status baru", { exact: true })).toHaveValue("DONE");
  await panel.getByLabel("Alasan", { exact: true }).fill("Equipment received");
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-task-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await panel.getByRole("button", { name: "Simpan tugas", exact: true }).click();
  await expect(panel).toHaveCount(0);
});
