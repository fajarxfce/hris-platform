import { expect, test } from "@playwright/test";
import { leaveId } from "./fixtures/leave";
import { companyIds } from "./identity-api";
import { installLeaveApi } from "./leave-api";

const query = `company=${companyIds[0]}`;
const directory = `/leave/requests?${query}`;
const detail = `/leave/requests/${leaveId()}?${query}`;

test("leave pages preserve their company, cursor and status filter through detail navigation", async ({
  page,
}) => {
  const api = await installLeaveApi(page);
  await page.goto(directory);
  const table = page.getByRole("table", { name: "Leave requests", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  await expect(table).toContainText("North Employee 01");
  await expect(table).not.toContainText("North Employee 22");
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  await table.getByRole("button", { name: new RegExp(`${leaveId(0, 22)}$`, "u") }).click();
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
    "North Employee 22",
  );
  await expect(
    page.getByRole("table", { name: "Submitted schedule (Asia/Jakarta)", exact: true }),
  ).toContainText("First half");
  await page.getByRole("link", { name: "Back to requests", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`after=${leaveId(0, 20)}$`, "u"));
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("PENDING");
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(21);
  await expect(table).not.toContainText("North Employee 01");
  expect(api.reads.at(-1)?.searchParams.get("status")).toBe("PENDING");
  expect(api.reads.at(-1)?.searchParams.has("after")).toBe(false);
});

test("history pages retain current metadata and the original approval and cancellation snapshots", async ({
  page,
}) => {
  await installLeaveApi(page);
  await page.goto(detail);
  const history = page.getByRole("table", { name: "Request history", exact: true });
  await expect(history.getByRole("row")).toHaveCount(21);
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
    "23",
  );
  await expect(
    page.getByRole("region", { name: "Cancellation approval", exact: true }).first(),
  ).toContainText("Cancelled");
  await page.getByRole("button", { name: "Older changes", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(5);
  await expect(history).toContainText("Submitted");
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
    "23",
  );
  await page.getByRole("button", { name: "Latest changes", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "View approval: Initial approval", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Approval request", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "View source request", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/leave/requests/${leaveId()}\\?${query}$`, "u"));
});

test("an assigned approver opens the source request without broad leave directory access", async ({
  page,
}) => {
  await installLeaveApi(page, ["approvals.read", "leave.approve"]);
  await page.goto(`/approvals?${query}`);
  await expect(page.getByRole("link", { name: "Leave requests", exact: true })).toHaveCount(0);
  await page
    .getByRole("table", { name: "Approval inbox", exact: true })
    .getByRole("button")
    .first()
    .click();
  await page.getByRole("link", { name: "View source request", exact: true }).click();
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
    "North Employee 02",
  );
});

for (const kind of ["list", "details"] as const)
  test(`company replacement discards a pending leave ${kind} and its cursor`, async ({ page }) => {
    const api = await installLeaveApi(page);
    const held = api.hold(kind);
    await page.goto(
      kind === "list" ? `${directory}&after=${leaveId(0, 20)}` : `${detail}&historyAfter=4`,
    );
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Leave requests", exact: true })).toContainText(
      "South Employee 01",
    );
    held.release();
    await expect(page).toHaveURL(new RegExp(`/leave/requests\\?company=${companyIds[1]}$`, "u"));
    await expect(page.getByRole("main")).not.toContainText("North Employee");
  });

test("invalid local filters do not issue requests and foreign-company identities remain unavailable", async ({
  page,
}) => {
  const api = await installLeaveApi(page);
  await page.goto(`${directory}&after=invalid`);
  await expect(page.getByRole("alert")).toContainText("Choose a valid");
  expect(api.reads).toHaveLength(0);
  await page.goto(`/leave/requests/${leaveId(1)}?${query}`);
  await expect(page.getByRole("alert")).toContainText("This leave request is unavailable");
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toHaveCount(0);
});

test("access failure clears the old leave evidence and can recover through explicit refresh", async ({
  page,
}) => {
  const api = await installLeaveApi(page);
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toBeVisible();
  api.fail("leave_request_not_found");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This leave request is unavailable");
  await expect(page.getByRole("main")).not.toContainText("Personal leave");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE TECHNICAL");
  api.fail(null);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toBeVisible();
});

for (const path of [directory, detail])
  test(`malformed leave data is not rendered at ${path}`, async ({ page }) => {
    const api = await installLeaveApi(page);
    api.malformed();
    await page.goto(path);
    await expect(page.getByRole("alert")).toContainText("response");
    await expect(page.getByRole("main")).not.toContainText("North Employee");
  });

test("leave detail supports Indonesian and dark mobile layout without restarting its read", async ({
  page,
}) => {
  const api = await installLeaveApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toBeVisible();
  const reads = api.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("button", { name: "Muat ulang", exact: true })).toHaveCSS(
    "color",
    "rgb(255, 255, 255)",
  );
  await expect(page.getByRole("heading", { name: "Detail cuti", exact: true })).toBeVisible();
  await expect(
    page.getByRole("table", { name: "Jadwal saat pengajuan (Asia/Jakarta)", exact: true }),
  ).toContainText("Paruh pertama");
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-leave-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});
