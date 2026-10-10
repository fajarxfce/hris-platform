import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { installLifecycleAssignmentApi, lifecycleMemberId } from "./lifecycle-assignment-api";
import { lifecycleAccountId, lifecycleCaseId } from "./lifecycle-cases-api";

const detail = `/people/lifecycle/cases/${lifecycleCaseId()}?company=${companyIds[0]}&status=OPEN`;
const queue = `/people/lifecycle/tasks?company=${companyIds[0]}`;
const panel = (page: Page) =>
  page.getByRole("dialog", { name: "Assign task: Review equipment", exact: true });
const picker = (page: Page) => page.getByRole("dialog", { name: "Choose member", exact: true });
async function open(page: Page) {
  await page.goto(detail);
  await page.getByRole("button", { name: "View task: Review equipment", exact: true }).click();
  await page.getByRole("button", { name: "Assign task", exact: true }).click();
  await expect(panel(page)).toBeVisible();
}
async function choose(page: Page, index = 1) {
  await panel(page).getByRole("button", { name: "Choose member", exact: true }).click();
  await picker(page)
    .getByRole("button", {
      name: `Select: North member ${index} · ${lifecycleMemberId(0, index)}`,
      exact: true,
    })
    .click();
}

test("manage-only operators assign from their queue using an on-demand bounded member lookup", async ({
  page,
}) => {
  const api = await installLifecycleAssignmentApi(page, {
    permissions: ["people.lifecycle.manage"],
  });
  await page.goto(queue);
  const task = page.getByRole("button", {
    name: "View task: Review equipment · North employee 1",
    exact: true,
  });
  await task.click();
  await page.getByRole("button", { name: "Assign task", exact: true }).click();
  expect(api.assigneeReads).toEqual([]);
  await panel(page).getByRole("button", { name: "Choose member", exact: true }).click();
  const results = picker(page).getByRole("table", { name: "Members", exact: true });
  await expect(results.getByRole("row")).toHaveCount(50);
  await expect(results).not.toContainText("Current member");
  await picker(page).getByRole("button", { name: "Next page", exact: true }).click();
  await expect(results.getByRole("row")).toHaveCount(3);
  expect(api.assigneeReads.at(-1)?.searchParams.get("after")).toBe(lifecycleMemberId(0, 49));
  await picker(page)
    .getByRole("button", {
      name: `Select: North member 51 · ${lifecycleMemberId(0, 51)}`,
      exact: true,
    })
    .click();
  await expect(picker(page)).toHaveCount(0);
  await panel(page).getByLabel("Reason", { exact: true }).fill("Assign equipment review");
  await panel(page).getByRole("button", { name: "Save assignment", exact: true }).click();
  await expect(panel(page)).toHaveCount(0);
  await expect(task).toHaveCount(0);
  expect(api.assignments[0]?.body).toEqual({
    expectedVersion: 50,
    assigneeId: lifecycleMemberId(0, 51),
    reason: "Assign equipment review",
  });
  expect(api.assignmentCommits).toBe(1);
  expect(api.reads.every((url) => url.pathname.endsWith("/tasks/assigned"))).toBe(true);
  expect(api.identity.unhandled).toEqual([]);
});

test("removing an assignee is explicit and retains a transition with its reason", async ({
  page,
}) => {
  const api = await installLifecycleAssignmentApi(page);
  await open(page);
  await panel(page).getByRole("button", { name: "Remove assignee", exact: true }).click();
  await expect(
    panel(page).getByRole("region", { name: "New assignee", exact: true }),
  ).toContainText("Unassigned");
  await panel(page).getByLabel("Reason", { exact: true }).fill("Return task to the team");
  await panel(page).getByRole("button", { name: "Save assignment", exact: true }).click();
  await expect(panel(page)).toHaveCount(0);
  await page.getByRole("button", { name: "View task: Review equipment", exact: true }).click();
  await page.getByRole("button", { name: "Assign task", exact: true }).click();
  await expect(
    panel(page).getByRole("button", { name: "Remove assignee", exact: true }),
  ).toHaveCount(0);
  await panel(page).getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await page
    .getByRole("group", { name: "History pages", exact: true })
    .getByRole("button", { name: "Next page", exact: true })
    .click();
  await page.getByRole("button", { name: "View change: 51", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Change details", exact: true })).toContainText(
    "Return task to the team",
  );
  expect(api.assignments[0]?.body.assigneeId).toBeNull();
  expect(api.assignmentCommits).toBe(1);
});

for (const mode of ["perform", "closed", "resolved"] as const) {
  test(`${mode} tasks cannot open assignment editing`, async ({ page }) => {
    const api = await installLifecycleAssignmentApi(page, {
      permissions:
        mode === "perform"
          ? ["people.lifecycle.read", "people.lifecycle.perform"]
          : ["people.lifecycle.read", "people.lifecycle.manage"],
    });
    const record = api.records.get(companyIds[0])?.[0];
    if (!record) throw new Error("Expected case");
    if (mode === "closed") record.status = "CANCELLED";
    if (mode === "resolved") {
      const task = record.tasks.find((item) => item.key === "equipment");
      if (!task) throw new Error("Expected task");
      task.status = "DONE";
      task.completedBy = lifecycleAccountId;
      task.completedAt = "2026-10-01T01:00:00Z";
    }
    await page.goto(detail);
    await page.getByRole("button", { name: "View task: Review equipment", exact: true }).click();
    await expect(page.getByRole("button", { name: "Assign task", exact: true })).toHaveCount(0);
    expect(api.assigneeReads).toEqual([]);
    expect(api.assignments).toEqual([]);
  });
}

test("an uncertain assignment preserves its member and operation through verification and a rejected retry", async ({
  page,
}) => {
  const api = await installLifecycleAssignmentApi(page);
  await open(page);
  await choose(page);
  await panel(page).getByLabel("Reason", { exact: true }).fill("Assign equipment review");
  api.loseAssignment();
  await panel(page).getByRole("button", { name: "Save assignment", exact: true }).click();
  await expect(panel(page).getByRole("status")).toContainText("The save could not be confirmed.");
  await expect(
    panel(page).getByRole("button", { name: "Choose member", exact: true }),
  ).toBeDisabled();
  api.identity.expireMfa();
  api.rejectAssignment("mfa_required", 403);
  await panel(page).getByRole("button", { name: "Retry save", exact: true }).click();
  await panel(page)
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await expect(panel(page)).not.toBeVisible();
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(panel(page)).toContainText("North member 1");
  expect(api.assignments).toHaveLength(2);
  api.removeMember(companyIds[0], lifecycleMemberId());
  api.rejectAssignment("lifecycle_assignee_unavailable", 422);
  await panel(page).getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(panel(page).getByRole("status")).toContainText("The save could not be confirmed.");
  await panel(page).getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(panel(page)).toHaveCount(0);
  expect(api.assignments).toHaveLength(4);
  for (const write of api.assignments) {
    expect(write.operation).toBe(api.assignments[0]?.operation);
    expect(write.body).toEqual(api.assignments[0]?.body);
  }
  expect(new Set(api.assignments.map((write) => write.csrf)).size).toBe(4);
  expect(api.assignmentCommits).toBe(1);
  expect(api.identity.unhandled).toEqual([]);
});

test("a missing choice is rejected locally and an unavailable member can be replaced after server rejection", async ({
  page,
}) => {
  const api = await installLifecycleAssignmentApi(page);
  await open(page);
  await panel(page).getByLabel("Reason", { exact: true }).fill("Assign equipment review");
  await panel(page).getByRole("button", { name: "Save assignment", exact: true }).click();
  await expect(panel(page).getByRole("alert")).toContainText("Choose a member");
  expect(api.assignments).toEqual([]);
  await choose(page);
  api.removeMember(companyIds[0], lifecycleMemberId());
  await panel(page).getByRole("button", { name: "Save assignment", exact: true }).click();
  await expect(panel(page).getByRole("alert")).toContainText("no longer eligible");
  await choose(page, 2);
  await panel(page).getByRole("button", { name: "Save assignment", exact: true }).click();
  await expect(panel(page)).toHaveCount(0);
  expect(api.assignments).toHaveLength(2);
  expect(api.assignments[1]?.body.assigneeId).toBe(lifecycleMemberId(0, 2));
  expect(api.assignments[1]?.operation).not.toBe(api.assignments[0]?.operation);
  expect(api.assignmentCommits).toBe(1);
});

test("switching between status and assignment forms protects edits and preserves the observed task", async ({
  page,
}) => {
  const api = await installLifecycleAssignmentApi(page);
  await page.goto(detail);
  await page.getByRole("button", { name: "View task: Review equipment", exact: true }).click();
  const status = page.getByRole("dialog", { name: "Review equipment", exact: true });
  await status.getByLabel("Reason", { exact: true }).fill("Status draft");
  await status.getByRole("button", { name: "Assign task", exact: true }).click();
  const departure = page.getByRole("dialog", { name: "Leave this page?", exact: true });
  await departure.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(status.getByRole("button", { name: "Assign task", exact: true })).toBeFocused();
  await expect(status.getByLabel("Reason", { exact: true })).toHaveValue("Status draft");
  await status.getByRole("button", { name: "Assign task", exact: true }).click();
  await departure.getByRole("button", { name: "Leave page", exact: true }).click();
  await panel(page).getByLabel("Reason", { exact: true }).fill("Assignment draft");
  await panel(page).getByRole("button", { name: "Back to task", exact: true }).click();
  await departure.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(
    panel(page).getByRole("button", { name: "Back to task", exact: true }),
  ).toBeFocused();
  await expect(panel(page).getByLabel("Reason", { exact: true })).toHaveValue("Assignment draft");
  await panel(page).getByRole("button", { name: "Back to task", exact: true }).click();
  await departure.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(status.getByLabel("Reason", { exact: true })).toHaveValue("");
  expect(api.completedReads.filter((url) => url.pathname.endsWith(lifecycleCaseId()))).toHaveLength(
    1,
  );
  expect(api.assignments).toEqual([]);
  expect(api.writes).toEqual([]);
});

test("closing a pending lookup discards its result when the company changes", async ({ page }) => {
  const api = await installLifecycleAssignmentApi(page);
  await open(page);
  const held = api.holdNextLookup();
  try {
    await panel(page).getByRole("button", { name: "Choose member", exact: true }).click();
    await held.entered;
    await picker(page).getByRole("button", { name: "Close", exact: true }).click();
    await expect(
      panel(page).getByRole("button", { name: "Choose member", exact: true }),
    ).toBeFocused();
    await panel(page).getByRole("button", { name: "Close", exact: true }).click();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Cases", exact: true })).toContainText(
      "South employee 1",
    );
    held.release();
    await expect(page.locator("body")).not.toContainText("North member");
    expect(api.assignments).toEqual([]);
    expect(api.identity.unhandled).toEqual([]);
  } finally {
    held.release();
  }
});

test("a pending assignment permits one request and ignores its late receipt after an approved departure", async ({
  page,
}) => {
  const api = await installLifecycleAssignmentApi(page);
  await open(page);
  await choose(page);
  await panel(page).getByLabel("Reason", { exact: true }).fill("Assign equipment review");
  const held = api.holdNextAssignment();
  try {
    await panel(page).getByRole("button", { name: "Save assignment", exact: true }).click();
    await held.entered;
    await expect(
      panel(page).getByRole("button", { name: "Save assignment", exact: true }),
    ).toBeDisabled();
    await panel(page).getByRole("button", { name: "Close", exact: true }).click();
    await page
      .getByRole("dialog", { name: "Leave this page?", exact: true })
      .getByRole("button", { name: "Stay on this page", exact: true })
      .click();
    await expect(panel(page).getByRole("button", { name: "Close", exact: true })).toBeFocused();
    await panel(page).getByRole("button", { name: "Close", exact: true }).click();
    await page
      .getByRole("dialog", { name: "Leave this page?", exact: true })
      .getByRole("button", { name: "Leave page", exact: true })
      .click();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Cases", exact: true })).toContainText(
      "South employee 1",
    );
    held.release();
    await expect.poll(() => api.assignmentCommits).toBe(1);
    await expect(page.locator("body")).not.toContainText("North member");
    await expect(page.locator("body")).not.toContainText("North employee");
    expect(api.assignments).toHaveLength(1);
  } finally {
    held.release();
  }
});

test("member search stays explicit and the assignment and picker fit Indonesian mobile layouts", async ({
  page,
}) => {
  const api = await installLifecycleAssignmentApi(page);
  await page.goto(detail);
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Lihat tugas: Review equipment", exact: true }).click();
  await page.getByRole("button", { name: "Tetapkan tugas", exact: true }).click();
  const assignment = page.getByRole("dialog", {
    name: "Tetapkan tugas: Review equipment",
    exact: true,
  });
  await assignment.getByRole("button", { name: "Pilih anggota", exact: true }).click();
  const members = page.getByRole("dialog", { name: "Pilih anggota", exact: true });
  await expect(members.getByRole("table", { name: "Anggota", exact: true })).toBeVisible();
  const before = api.assigneeReads.length;
  await members.getByLabel("Cari berdasarkan nama", { exact: true }).fill("North member 51");
  expect(api.assigneeReads).toHaveLength(before);
  await members.getByRole("button", { name: "Cari", exact: true }).click();
  await expect(
    members.getByRole("table", { name: "Anggota", exact: true }).getByRole("row"),
  ).toHaveCount(2);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-assignee-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await members
    .getByRole("button", {
      name: `Pilih: North member 51 · ${lifecycleMemberId(0, 51)}`,
      exact: true,
    })
    .click();
  await assignment.getByLabel("Alasan", { exact: true }).fill("Assign equipment review");
  await page.screenshot({
    path: "../../.work/dashboard-lifecycle-assignment-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await assignment.getByRole("button", { name: "Simpan penugasan", exact: true }).click();
  await expect(assignment).toHaveCount(0);
  expect(api.assignmentCommits).toBe(1);
});
