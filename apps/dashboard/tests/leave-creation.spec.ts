import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { installLeaveCreationApi } from "./leave-creation-api";

const query = `company=${companyIds[0]}`;
const create = `/leave/requests/new?${query}`;

async function chooseDates(page: Page) {
  await page.getByRole("button", { name: "Choose employee", exact: true }).click();
  const employee = page.getByRole("dialog", { name: "Choose employee", exact: true });
  await employee.getByRole("button", { name: "Select: North Employee 01", exact: true }).click();
  await expect(employee).toHaveCount(0);
  await page.getByLabel("Start date", { exact: true }).fill("2026-10-05");
  await page.getByLabel("End date", { exact: true }).fill("2026-10-06");
  await page.getByRole("button", { name: "Choose leave type", exact: true }).click();
  await page.getByRole("button", { name: "Select: Annual leave", exact: true }).click();
  await page.getByRole("combobox", { name: "First day", exact: true }).selectOption("FIRST_HALF");
  await page.getByLabel("Reason", { exact: true }).fill("Family appointment");
}
async function review(page: Page) {
  await page.getByRole("button", { name: "Review request", exact: true }).click();
  await expect(
    page.getByRole("complementary", { name: "Request review", exact: true }),
  ).toContainText("0.5");
  await expect(page.getByRole("button", { name: "Submit request", exact: true })).toBeEnabled();
}

test("administrators select dated leave, review the server calculation and open the created request", async ({
  page,
}) => {
  const api = await installLeaveCreationApi(page);
  await page.goto(`/leave/requests?${query}&status=PENDING`);
  await page.getByRole("link", { name: "New request", exact: true }).click();
  await chooseDates(page);
  await review(page);
  const calculation = page.getByRole("complementary", { name: "Request review", exact: true });
  await expect(
    calculation.getByRole("table", { name: "Balance after submission", exact: true }),
  ).toContainText("12.5");
  await expect(
    calculation.getByRole("table", { name: "Days included", exact: true }),
  ).toContainText("First half");
  await expect(calculation).toContainText("Dates excluded by the work schedule");
  expect(api.previews).toEqual([
    {
      employeeId: "b2000000-0000-4000-8000-000000000001",
      typeId: "b3000000-0000-4000-8000-000000000001",
      days: [
        { workDate: "2026-10-05", portion: "FIRST_HALF" },
        { workDate: "2026-10-06", portion: "FULL" },
      ],
    },
  ]);
  await page.screenshot({
    path: "../../.work/dashboard-leave-creation-desktop.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Submit request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Leave request submitted.");
  expect(api.writes[0]?.body).toMatchObject({
    ...api.previews[0],
    reason: "Family appointment",
    attachmentRevisionIds: [],
    previewRevision: "a".repeat(64),
  });
  await page.getByRole("link", { name: "View request", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`company=${companyIds[0]}&status=PENDING$`, "u"));
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
    "North Employee 01",
  );
  await expect(page.getByRole("table", { name: "Request history", exact: true })).toContainText(
    "Family appointment",
  );
});

test("required employee evidence and a reason are checked before a submission reaches HTTP", async ({
  page,
}) => {
  const api = await installLeaveCreationApi(page);
  api.requireEvidence();
  await page.goto(create);
  await chooseDates(page);
  await review(page);
  await page.getByRole("button", { name: "Submit request", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("document");
  expect(api.writes).toEqual([]);
  await page.getByRole("button", { name: "Choose documents", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Choose documents", exact: true });
  await picker.getByRole("button", { name: "Select: Supporting letter", exact: true }).click();
  await expect(
    picker.getByRole("button", { name: "Select: Supporting letter", exact: true }),
  ).toBeDisabled();
  await picker.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("   ");
  await page.getByRole("button", { name: "Submit request", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("reason");
  expect(api.writes).toEqual([]);
  await page.getByLabel("Reason", { exact: true }).fill("Required evidence attached");
  await page.getByRole("button", { name: "Submit request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Leave request submitted.");
  expect(api.writes[0]?.body.attachmentRevisionIds).toEqual([
    "d2000000-0000-4000-8000-000000000001",
  ]);
  expect(api.previews).toHaveLength(1);
});

test("changing dates aborts a pending preview and requires another calculation", async ({
  page,
}) => {
  const api = await installLeaveCreationApi(page);
  await page.goto(create);
  await chooseDates(page);
  const held = api.holdPreparation("preview");
  await page.getByRole("button", { name: "Review request", exact: true }).click();
  await held.entered;
  await page.getByLabel("End date", { exact: true }).fill("2026-10-07");
  held.release();
  await expect(page.getByRole("button", { name: "Submit request", exact: true })).toHaveCount(0);
  await expect(page.getByRole("table", { name: "Days included", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Review request", exact: true }).click();
  await expect(
    page.getByRole("complementary", { name: "Request review", exact: true }),
  ).toContainText("1.5");
  await expect(page.getByRole("button", { name: "Submit request", exact: true })).toBeEnabled();
  expect(api.previews).toHaveLength(2);
  expect(api.previews[1]?.days).toHaveLength(3);
  expect(api.writes).toEqual([]);
});

test("employee discovery pages and searches explicitly; changing employees removes old evidence", async ({
  page,
}) => {
  const api = await installLeaveCreationApi(page);
  await page.goto(create);
  await chooseDates(page);
  await page.getByRole("button", { name: "Choose documents", exact: true }).click();
  await page.getByRole("button", { name: "Select: Supporting letter", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("button", { name: "Change employee", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Choose employee", exact: true });
  await picker.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(picker.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  await picker.getByRole("button", { name: "First page", exact: true }).click();
  await expect(
    picker.getByRole("button", { name: "Select: North Employee 01", exact: true }),
  ).toBeVisible();
  const requests = api.preparationReads.length;
  await picker.getByLabel("Search employees", { exact: true }).fill("North Employee 22");
  expect(api.preparationReads).toHaveLength(requests);
  await picker.getByRole("button", { name: "Search", exact: true }).click();
  await picker.getByRole("button", { name: "Select: North Employee 22", exact: true }).click();
  await expect(page.getByRole("main")).not.toContainText("Supporting letter");
  await expect(page.getByRole("main")).toContainText("North Employee 22");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  expect(api.preparationReads.at(-1)?.searchParams.get("query")).toBe("North Employee 22");
});

test("lost submission survives MFA and a later conflict without changing the original command", async ({
  page,
}) => {
  const api = await installLeaveCreationApi(page);
  await page.goto(create);
  await chooseDates(page);
  await review(page);
  api.loseNext();
  await page.getByRole("button", { name: "Submit request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("submission result is unconfirmed");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
  api.identity.expireMfa();
  api.rejectNext("submit", "mfa_required", 403);
  await page.getByRole("button", { name: "Retry submission", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  api.rejectNext("submit", "leave_preview_changed");
  await page.getByRole("button", { name: "Retry submission", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("submission result is unconfirmed");
  await page.getByRole("button", { name: "Retry submission", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Leave request submitted.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(4);
  expect(api.previews).toHaveLength(1);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
});

test("a definitive preview conflict requires review again before issuing a new command", async ({
  page,
}) => {
  const api = await installLeaveCreationApi(page);
  await page.goto(create);
  await chooseDates(page);
  await review(page);
  api.rejectNext("submit", "leave_preview_changed");
  await page.getByRole("button", { name: "Submit request", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Review");
  await expect(page.getByRole("button", { name: "Submit request", exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Family appointment");
  await review(page);
  await page.getByRole("button", { name: "Submit request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Leave request submitted.");
  expect(api.commits).toBe(1);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
  expect(api.previews).toHaveLength(2);
});

test("read-only access does not discover employees or prepare an administrative command", async ({
  page,
}) => {
  const api = await installLeaveCreationApi(page, ["leave.read"]);
  await page.goto(`/leave/requests?${query}`);
  await expect(page.getByRole("table", { name: "Leave requests", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "New request", exact: true })).toHaveCount(0);
  await page.goto(create);
  await expect(page.getByRole("alert")).toContainText("access");
  await expect(page.getByRole("button", { name: "Choose employee", exact: true })).toBeDisabled();
  expect(api.preparationReads).toEqual([]);
  expect(api.previews).toEqual([]);
  expect(api.writes).toEqual([]);
});

test("offline discovery finishes with an error and resumes only on explicit retry", async ({
  page,
  context,
}) => {
  await installLeaveCreationApi(page);
  let requests = 0,
    disconnected = true;
  await page.route("**/leave/preparation/employees?**", async (route) => {
    requests += 1;
    return disconnected ? route.abort("internetdisconnected") : route.fallback();
  });
  await page.goto(create);
  await expect(page.getByRole("heading", { name: "New leave request", exact: true })).toBeVisible();
  await context.setOffline(true);
  await page.getByRole("button", { name: "Choose employee", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Choose employee", exact: true });
  await expect(picker.getByRole("alert")).toContainText("connect");
  await expect(picker.getByRole("progressbar")).toHaveCount(0);
  expect(requests).toBe(1);
  disconnected = false;
  await context.setOffline(false);
  await expect(picker.getByRole("button", { name: "Retry", exact: true })).toBeVisible();
  expect(requests).toBe(1);
  await picker.getByRole("button", { name: "Retry", exact: true }).click();
  await expect(
    picker.getByRole("button", { name: "Select: North Employee 01", exact: true }),
  ).toBeVisible();
  expect(requests).toBe(2);
});

test("company replacement disposes the pending submission and ignores its late receipt", async ({
  page,
}) => {
  const api = await installLeaveCreationApi(page);
  await page.goto(create);
  await chooseDates(page);
  await review(page);
  const held = api.holdPreparation("submit");
  await page.getByRole("button", { name: "Submit request", exact: true }).click();
  await held.entered;
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("table", { name: "Leave requests", exact: true })).toContainText(
    "South Employee",
  );
  held.release();
  await expect(page).toHaveURL(new RegExp(`/leave/requests\\?company=${companyIds[1]}$`, "u"));
  await expect(page.getByRole("main")).not.toContainText("North Employee");
  await expect(page.getByRole("main")).not.toContainText("Leave request submitted");
  expect(api.writes).toHaveLength(1);
  expect(api.writes[0]?.company).toBe(companyIds[0]);
});

test("Indonesian dark narrow layout preserves the reviewed selection without new requests", async ({
  page,
}) => {
  const api = await installLeaveCreationApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(create);
  await chooseDates(page);
  await review(page);
  const reads = api.preparationReads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Pengajuan cuti baru", exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Family appointment");
  await expect(page.getByRole("button", { name: "Ajukan cuti", exact: true })).toBeEnabled();
  expect(api.preparationReads).toHaveLength(reads);
  expect(api.previews).toHaveLength(1);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-leave-creation-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});
