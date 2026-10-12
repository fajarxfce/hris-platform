import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { installOvertimeActionsApi } from "./overtime-actions-api";
import { overtimeRecordId } from "./overtime-api";

test.use({ timezoneId: "Pacific/Honolulu" });
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-13T12:00:00Z"));
});
const query = `company=${companyIds[0]}&from=2026-10-01&until=2026-10-31`;
const path = `/workforce/overtime/${overtimeRecordId()}`;
const detail = `${path}?${query}`;
async function open(page: Page, intent = "approve") {
  await page.goto(`${path}/${intent}?${query}`);
  await expect(page.getByRole("region", { name: "Request review", exact: true })).toContainText(
    "North Employee 1",
  );
}

test("actual submission reviews current facts and converts the request timezone without changing directory context", async ({
  page,
}) => {
  const api = await installOvertimeActionsApi(page, true);
  await page.goto(`${detail}&status=PLANNED&after=${overtimeRecordId(0, 0)}`);
  await page.getByRole("link", { name: "Submit actual time", exact: true }).click();
  await expect(page.getByLabel("Actual start", { exact: true })).toHaveValue("2026-10-12T14:00");
  expect(api.reads).toHaveLength(2);
  expect(api.reads.at(-1)?.searchParams.has("historyAfter")).toBe(false);
  await page.getByLabel("Actual start", { exact: true }).fill("2026-10-12T14:15");
  await page.getByLabel("Actual end", { exact: true }).fill("2026-10-12T16:45");
  await page.getByLabel("Break (min)", { exact: true }).fill("15");
  await page.getByLabel("Reason", { exact: true }).fill("Inventory completed");
  await page.getByRole("button", { name: "Submit actual time", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Actual time submitted for review.");
  expect(api.writes[0]).toMatchObject({
    endpoint: "actual",
    company: companyIds[0],
    id: overtimeRecordId(),
    body: {
      expectedVersion: 0,
      reason: "Inventory completed",
      actual: {
        startsAt: "2026-10-12T07:15:00Z",
        endsAt: "2026-10-12T09:45:00Z",
        breakMinutes: 15,
      },
    },
  });
  expect(api.writes[0]?.body.actual).not.toHaveProperty("workedMinutes");
  await page.getByRole("link", { name: "View current request", exact: true }).click();
  await expect(page).toHaveURL(`${detail}&status=PLANNED&after=${overtimeRecordId(0, 0)}`);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Pending review",
  );
  await expect(page.getByRole("table", { name: "Time records", exact: true })).toContainText("135");
  expect(api.commits).toBe(1);
  expect(api.identity.unhandled).toEqual([]);
});

test("an intermediate approval records its stage without claiming final approval", async ({
  page,
}) => {
  const api = await installOvertimeActionsApi(page);
  await open(page);
  await page.getByRole("button", { name: "Approve overtime", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Approval recorded.");
  expect(api.record.request.status).toBe("PENDING");
  expect(api.record.request.approvedMinutes).toBe(0);
  expect(api.writes[0]?.body).toEqual({ expectedVersion: 1, decision: "APPROVE", reason: "" });
  await page.getByRole("link", { name: "View current request", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Pending review",
  );
  await expect(page.getByRole("region", { name: "Approval", exact: true }).first()).toContainText(
    "2 / 2",
  );
});

test("rejection requires a reason before delivery", async ({ page }) => {
  const api = await installOvertimeActionsApi(page);
  await open(page, "reject");
  await page.getByRole("button", { name: "Reject overtime", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(/reason/iu);
  expect(api.writes).toEqual([]);
  await page.getByLabel("Reason", { exact: true }).fill("Recorded work could not be verified");
  await page.getByRole("button", { name: "Reject overtime", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Request rejected.");
  expect(api.record.request.status).toBe("REJECTED");
  expect(api.writes[0]?.body.decision).toBe("REJECT");
});

for (const planned of [true, false])
  test(`withdrawal handles an unfinished ${planned ? "plan" : "review"}`, async ({ page }) => {
    const api = await installOvertimeActionsApi(page, planned);
    await page.goto(detail);
    await page.getByRole("link", { name: "Withdraw overtime", exact: true }).click();
    await page.getByLabel("Reason", { exact: true }).fill("Work no longer required");
    await page.getByRole("button", { name: "Withdraw overtime", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Request withdrawn.");
    await page.getByRole("link", { name: "View current request", exact: true }).click();
    await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
      "Withdrawn",
    );
    expect(api.record.request.approvedMinutes).toBe(0);
    expect(api.record.approval?.status ?? null).toBe(planned ? null : "CANCELLED");
    expect(api.writes[0]?.endpoint).toBe("withdraw");
  });

test("invalid actual text or time outside the plan does not reach HTTP", async ({ page }) => {
  const api = await installOvertimeActionsApi(page, true);
  await open(page, "actual");
  await page.getByLabel("Reason", { exact: true }).fill("Actual review");
  await page.getByLabel("Actual start", { exact: true }).fill("");
  await page.getByRole("button", { name: "Submit actual time", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Enter valid local start");
  await page.getByLabel("Actual start", { exact: true }).fill("2026-10-12T14:00");
  await page.getByLabel("Actual end", { exact: true }).fill("2026-10-12T18:00");
  await page.getByRole("button", { name: "Submit actual time", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(/requested/iu);
  expect(api.writes).toEqual([]);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Actual review");
});

test("read grants cannot reveal actions or acquire their review", async ({ page }) => {
  const api = await installOvertimeActionsApi(page, false, ["overtime.read"]);
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Approve overtime", exact: true })).toHaveCount(0);
  const reads = api.reads.length;
  await page.goto(`${path}/approve?${query}`);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("region", { name: "Request review", exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(reads);
  expect(api.writes).toEqual([]);
});

test("server capabilities still gate an assigned-action route", async ({ page }) => {
  const api = await installOvertimeActionsApi(page);
  api.record.actions = [];
  await page.goto(`${path}/approve?${query}`);
  await expect(page.getByRole("alert")).toContainText("no longer available");
  await expect(page.getByRole("button", { name: "Approve overtime", exact: true })).toHaveCount(0);
  expect(api.writes).toEqual([]);
});

test("a lost receipt survives MFA recovery and a later conflict while denied details stay hidden", async ({
  page,
}) => {
  const api = await installOvertimeActionsApi(page);
  await open(page);
  await page.getByLabel("Reason", { exact: true }).fill("Original decision");
  const reads = api.reads.length;
  api.loseNext();
  await page.getByRole("button", { name: "Approve overtime", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result could not be confirmed");
  api.identity.expireMfa();
  api.rejectWrite("mfa_required", 403);
  await page.getByRole("button", { name: "Retry original action", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Request review", exact: true })).toHaveCount(0);
  api.rejectWrite("stale_version");
  await page.getByRole("button", { name: "Retry original action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result could not be confirmed");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  const held = api.holdWrite();
  try {
    await page.getByRole("button", { name: "Retry original action", exact: true }).click();
    await held.started;
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    await expect(page.getByRole("main")).not.toContainText("Original decision");
    held.release();
    await expect(page.getByRole("status")).toContainText("Approval recorded.");
  } finally {
    held.release();
  }
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(4);
  expect(api.reads).toHaveLength(reads);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
});

test("an initial conflict requires protected re-review before a new command", async ({ page }) => {
  const api = await installOvertimeActionsApi(page);
  await open(page);
  await page.getByLabel("Reason", { exact: true }).fill("Old review");
  api.competingReview();
  await page.getByRole("button", { name: "Approve overtime", exact: true }).click();
  await expect(page.getByRole("button", { name: "Approve overtime", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Review again", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Old review");
  await page.getByRole("button", { name: "Review again", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  expect(api.writes).toHaveLength(1);
  await page.getByLabel("Reason", { exact: true }).fill("Current review");
  await page.getByRole("button", { name: "Approve overtime", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Approval recorded.");
  expect(api.writes[1]?.body.expectedVersion).toBe(2);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
});

test("read retries do not expose the last review after resource access is denied", async ({
  page,
}) => {
  const api = await installOvertimeActionsApi(page);
  await open(page, "withdraw");
  await page.getByLabel("Reason", { exact: true }).fill("Work not required");
  api.rejectWrite("overtime_request_not_found", 404);
  await page.getByRole("button", { name: "Withdraw overtime", exact: true }).click();
  await expect(page.getByRole("region", { name: "Request review", exact: true })).toHaveCount(0);
  const held = api.holdActionRead();
  api.rejectRead("overtime_request_not_found");
  try {
    await page.getByRole("button", { name: "Retry", exact: true }).click();
    await held.started;
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    held.release();
    await expect(page.getByRole("button", { name: "Retry", exact: true })).toBeVisible();
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    await expect(page.getByRole("main")).not.toContainText("PRIVATE DETAIL");
    await page.getByRole("button", { name: "Retry", exact: true }).click();
    await expect(page.getByRole("region", { name: "Request review", exact: true })).toBeVisible();
    expect(api.writes).toHaveLength(1);
  } finally {
    held.release();
  }
});

test("company replacement aborts a held review and discards the old filters", async ({ page }) => {
  const api = await installOvertimeActionsApi(page);
  const held = api.holdActionRead();
  try {
    await page.goto(`${path}/approve?${query}&after=${overtimeRecordId(0, 0)}`);
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page).toHaveURL(
      `/workforce/overtime?company=${companyIds[1]}&from=2026-10-01&until=2026-10-31`,
    );
    await expect(page.getByRole("table", { name: "Overtime requests", exact: true })).toContainText(
      "South Employee",
    );
    held.release();
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    expect(api.writes).toEqual([]);
  } finally {
    held.release();
  }
});

test("leaving an in-flight submission cannot place its late receipt in another company", async ({
  page,
}) => {
  const api = await installOvertimeActionsApi(page);
  await open(page);
  const held = api.holdWrite();
  try {
    await page.getByRole("button", { name: "Approve overtime", exact: true }).click();
    await held.started;
    await expect(
      page.getByRole("button", { name: "Approve overtime", exact: true }),
    ).toBeDisabled();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("table", { name: "Overtime requests", exact: true })).toContainText(
      "South Employee",
    );
    held.release();
    await expect(page.getByRole("main")).not.toContainText("Approval recorded");
    expect(api.writes).toHaveLength(1);
    expect(api.writes[0]?.company).toBe(companyIds[0]);
  } finally {
    held.release();
  }
});

test("English light and Indonesian dark forms stay contained and preserve unsaved input", async ({
  page,
}) => {
  const api = await installOvertimeActionsApi(page, true);
  await page.setViewportSize({ width: 390, height: 844 });
  await open(page, "actual");
  await page.getByLabel("Actual start", { exact: true }).fill("2026-10-12T14:15");
  await page.getByLabel("Reason", { exact: true }).fill("Pemeriksaan inventaris");
  const reads = api.reads.length;
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-overtime-actual-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("link", { name: "Back to request", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Ajukan waktu aktual", exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Mulai aktual", { exact: true })).toHaveValue("2026-10-12T14:15");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Pemeriksaan inventaris");
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-overtime-actual-dark.png",
    fullPage: true,
    animations: "disabled",
  });
});
