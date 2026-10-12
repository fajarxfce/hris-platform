import { expect, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { installOvertimeApi, overtimeRecordId } from "./overtime-api";

test.use({ timezoneId: "Pacific/Honolulu" });
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-13T12:00:00Z"));
});
const path = "/workforce/overtime",
  scope = `company=${companyIds[0]}&from=2026-10-01&until=2026-10-31`;
const directory = `${path}?${scope}`,
  detail = `${path}/${overtimeRecordId()}?${scope}`;

test("overtime filters and paging survive detail navigation without using the browser timezone", async ({
  page,
}) => {
  const api = await installOvertimeApi(page);
  await page.goto(path);
  await expect(page).toHaveURL(directory);
  const table = page.getByRole("table", { name: "Overtime requests", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await expect(table).toContainText("North Employee 1");
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(4);
  await table.getByRole("row").filter({ hasText: "North Employee 51" }).getByRole("button").click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "North Employee 51",
  );
  await expect(page.getByRole("table", { name: "Time records", exact: true })).toContainText(
    "Oct 12, 2026, 2:00 PM",
  );
  await page.getByRole("link", { name: "Back to overtime", exact: true }).click();
  await expect(page).toHaveURL(`${directory}&after=${overtimeRecordId(0, 50)}`);
  await expect(table.getByRole("row")).toHaveCount(4);
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("PENDING");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(28);
  expect(api.reads.at(-1)?.searchParams.get("status")).toBe("PENDING");
  expect(api.reads.at(-1)?.searchParams.has("after")).toBe(false);
  const reads = api.reads.length;
  await page.getByLabel("From", { exact: true }).fill("2026-12-01");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(reads);
  expect(api.identity.unhandled).toHaveLength(0);
});

test("default overtime dates follow the company's month across a browser timezone boundary", async ({
  page,
}) => {
  await page.clock.setFixedTime(new Date("2026-10-31T18:00:00Z"));
  const api = await installOvertimeApi(page);
  await page.goto(path);
  await expect(
    page.getByText("No overtime requests match these filters.", { exact: true }),
  ).toBeVisible();
  expect(api.reads[0]?.searchParams.get("from")).toBe("2026-11-01");
  expect(api.reads[0]?.searchParams.get("until")).toBe("2026-11-30");
});

test("history retains current facts while paging and opens a change without repeating acquisition", async ({
  page,
}) => {
  const api = await installOvertimeApi(page);
  await page.goto(detail);
  const history = page.getByRole("table", { name: "History", exact: true });
  await expect(history.getByRole("row")).toHaveCount(51);
  const button = page.getByRole("button", { name: "View change: 52", exact: true });
  await button.click();
  const panel = page.getByRole("dialog", { name: "Change details", exact: true });
  await expect(panel).toContainText("Work verified");
  await expect(panel).toContainText("Oct 12, 2026, 2:15 PM");
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  await expect(button).toBeFocused();
  expect(api.reads).toHaveLength(1);
  await page.getByRole("button", { name: "Older changes", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(4);
  await expect(page).toHaveURL(`${detail}&historyAfter=3`);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Pending review",
  );
  await page.getByRole("button", { name: "View change: 0", exact: true }).click();
  await expect(panel).toContainText("Inventory count 1");
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("button", { name: "Latest changes", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(51);
  expect(api.reads.map((url) => url.searchParams.get("historyAfter"))).toEqual([null, "3", null]);
  await page.getByRole("link", { name: "View approval", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Approval request", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "View source request", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Overtime request", exact: true })).toBeVisible();
  expect(api.identity.unhandled).toHaveLength(0);
});

test("assigned reviewers can open their source request without a company-wide overtime directory", async ({
  page,
}) => {
  const api = await installOvertimeApi(page, ["overtime.approve", "approvals.read"]);
  await page.goto(`/approvals?company=${companyIds[0]}`);
  await page
    .getByRole("table", { name: "Approval inbox", exact: true })
    .getByRole("button")
    .first()
    .click();
  await page.getByRole("link", { name: "View source request", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "North Employee 1",
  );
  await expect(page.getByRole("link", { name: "Back to approvals", exact: true })).toBeVisible();
  await expect(
    page.getByRole("navigation").getByRole("link", { name: "Overtime", exact: true }),
  ).toHaveCount(0);
  const reads = api.reads.length;
  await page.goto(directory);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(reads);
});

for (const kind of ["list", "details"] as const) {
  test(`company replacement cancels a pending overtime ${kind} and discards its identities and cursors`, async ({
    page,
  }) => {
    const api = await installOvertimeApi(page),
      held = api.holdRead(kind);
    try {
      await page.goto(
        kind === "list"
          ? `${directory}&after=${overtimeRecordId(0, 50)}`
          : `${detail}&historyAfter=3`,
      );
      await held.started;
      await page
        .getByRole("combobox", { name: "Company", exact: true })
        .selectOption(companyIds[1]);
      await expect(page).toHaveURL(
        `${path}?company=${companyIds[1]}&from=2026-10-01&until=2026-10-31`,
      );
      await expect(
        page.getByRole("table", { name: "Overtime requests", exact: true }),
      ).toContainText("South Employee 1");
      held.release();
      await expect(page.getByRole("main")).not.toContainText("North Employee");
      expect(api.reads.at(-1)?.searchParams.has("after")).toBe(false);
      expect(api.reads.at(-1)?.searchParams.has("historyAfter")).toBe(false);
    } finally {
      held.release();
    }
  });
}

test("refresh clears private overtime evidence until a fresh result is admitted", async ({
  page,
}) => {
  const api = await installOvertimeApi(page);
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toBeVisible();
  const held = api.holdRead("details");
  api.rejectNext("overtime_request_not_found");
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.started;
    await expect(page.getByRole("region", { name: "Overview", exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Refresh", exact: true })).toBeDisabled();
    held.release();
    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    await expect(page.getByRole("main")).not.toContainText("PRIVATE TECHNICAL ERROR");
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
      "North Employee 1",
    );
  } finally {
    held.release();
  }
});

test("MFA verification cannot restore a rejected overtime result without explicit acquisition", async ({
  page,
}) => {
  const api = await installOvertimeApi(page);
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toBeVisible();
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(2);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toBeVisible();
  expect(api.reads).toHaveLength(3);
});

for (const target of [
  `${directory}&after=invalid`,
  `${path}?company=${companyIds[0]}&from=2026-01-01&until=2026-12-31`,
  `${detail}&historyAfter=01`,
  `${detail}&historyAfter=9007199254740992`,
]) {
  test(`invalid overtime input does not reach transport: ${target}`, async ({ page }) => {
    const api = await installOvertimeApi(page);
    await page.goto(target);
    await expect(page.getByRole("alert")).toBeVisible();
    expect(api.reads).toHaveLength(0);
  });
}

for (const target of [directory, detail]) {
  test(`malformed overtime data remains unavailable: ${target}`, async ({ page }) => {
    const api = await installOvertimeApi(page);
    api.malformNext();
    await page.goto(target);
    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page.getByRole("main")).not.toContainText("North Employee");
  });
}

test("overtime detail and history fit narrow English/Indonesian and light/dark layouts", async ({
  page,
}) => {
  const api = await installOvertimeApi(page);
  await page.setViewportSize({ width: 430, height: 932 });
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toBeVisible();
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-overtime-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Detail lembur", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Lihat perubahan: 52", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Detail perubahan", exact: true });
  await expect(panel).toContainText("Asia/Jakarta");
  await expect
    .poll(() =>
      panel.evaluate((element) => {
        const bounds = element.getBoundingClientRect();
        return bounds.left >= 0 && Math.round(bounds.right) <= window.innerWidth;
      }),
    )
    .toBe(true);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-overtime-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.reads).toHaveLength(1);
  expect(api.identity.unhandled).toHaveLength(0);
});
