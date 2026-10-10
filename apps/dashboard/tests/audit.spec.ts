import { expect, type Page, test } from "@playwright/test";
import { companyIds, installIdentityApi } from "./identity-api";

const permissions = ["company.read", "audit.read"];
const path = "/administration/audit";
const from = "2026-09-01T00:00:00.000123Z";
const until = "2026-10-01T00:00:00.000123Z";
const eventId = (value: number) => `40000000-0000-4000-8000-${String(value).padStart(12, "0")}`;
const actorId = "20000000-0000-4000-8000-000000000001";
const resourceId = "30000000-0000-4000-8000-000000000001";

function response(companyId: string, url: URL, paginate = false) {
  const query = url.searchParams;
  const count = paginate && !query.has("cursor") ? 50 : 2;
  const items = Array.from({ length: count }, (_, index) => ({
    id: eventId(count === 50 ? 100 - index : 2 - index),
    companyId,
    actorId,
    resourceType: "employment",
    resourceId,
    action: "employment.created",
    correlationId: "50000000-0000-4000-8000-000000000001",
    recordedAt: "2026-09-25T03:15:00Z",
  })).filter((event) =>
    ["action", "resourceType", "resourceId", "actorId"].every(
      (key) => !query.has(key) || query.get(key) === event[key as keyof typeof event],
    ),
  );
  return {
    companyId,
    from: query.get("from") ?? from,
    until: query.get("until") ?? until,
    evaluatedAt: "2026-10-10T00:00:00.000000123Z",
    items,
    nextCursor: items.length === 50 ? eventId(51) : null,
  };
}

async function installAudit(page: Page, paginate = false) {
  const requests: URL[] = [];
  let failure: string | null = null;
  await page.route("**/api/v1/companies/*/audit-events?*", async (route) => {
    const url = new URL(route.request().url());
    requests.push(url);
    expect(route.request().headers()["x-hris-client-platform"]).toBe("WEB");
    expect(route.request().headers()["x-hris-client-build"]).toBe("1");
    expect(url.searchParams.get("limit")).toBe("50");
    return failure
      ? route.fulfill({
          status: 403,
          json: { code: failure, fields: {}, parameters: {}, detail: "PRIVATE AUDIT FAILURE" },
        })
      : route.fulfill({ json: response(url.pathname.split("/")[4] ?? "", url, paginate) });
  });
  return {
    requests,
    fail: (code: string | null) => {
      failure = code;
    },
  };
}

test("audit shows localized metadata and keyboard-accessible details across responsive themes", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installAudit(page);
  await page.goto(path);
  await expect(page.getByRole("link", { name: "Audit log", exact: true })).toHaveAttribute(
    "aria-current",
    "page",
  );
  const table = page.getByRole("table", { name: "Events", exact: true });
  await expect(table.getByRole("row")).toHaveCount(3);
  await expect(table).toContainText("employment.created");
  await page.screenshot({
    path: "../../.work/dashboard-audit-light.png",
    fullPage: true,
    animations: "disabled",
  });
  const details = table.getByRole("button", { name: /^View details:/u }).first();
  await details.focus();
  await page.keyboard.press("Enter");
  const dialog = page.getByRole("dialog", { name: "Audit event", exact: true });
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText(resourceId);
  await expect(dialog).toContainText(actorId);
  await expect(dialog).toContainText(companyIds[0]);
  await expect(dialog).toContainText("2026-09-25T03:15:00Z");
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
  await expect(details).toBeFocused();
  const loaded = api.requests.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Log audit", exact: true })).toBeVisible();
  expect(
    await page
      .getByLabel("Mulai (UTC)", { exact: true })
      .evaluate((element) => getComputedStyle(element).colorScheme),
  ).toBe("dark");
  await page.screenshot({
    path: "../../.work/dashboard-audit-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: "../../.work/dashboard-audit-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page
    .getByRole("button", { name: /^Lihat detail:/u })
    .first()
    .click();
  await expect(page.getByRole("dialog", { name: "Event audit", exact: true })).toBeVisible();
  await page.screenshot({
    path: "../../.work/dashboard-audit-details-mobile.png",
    fullPage: false,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  expect(api.requests).toHaveLength(loaded);
  expect(identity.unhandled).toEqual([]);
});

test("audit pagination keeps exact server time bounds and restores filters through browser history", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installAudit(page, true);
  await page.goto(`${path}?action=employment.created&actorId=${actorId}`);
  const table = page.getByRole("table", { name: "Events", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await expect(page.getByRole("button", { name: "First page", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Older events", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  expect(Object.fromEntries(api.requests.at(-1)?.searchParams ?? [])).toEqual({
    from,
    until,
    cursor: eventId(51),
    action: "employment.created",
    actorId,
    limit: "50",
  });
  await expect(page.getByRole("button", { name: "Older events", exact: true })).toBeDisabled();
  await expect(page.getByLabel("From (UTC)", { exact: true })).toHaveValue("2026-09-01T00:00");
  await page.goBack();
  await expect(table.getByRole("row")).toHaveCount(51);
  await expect(page.getByLabel("From (UTC)", { exact: true })).toHaveValue("");
  await page.goForward();
  await expect(table.getByRole("row")).toHaveCount(3);
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(51);
  expect(api.requests.at(-1)?.searchParams.has("cursor")).toBe(false);
  expect(api.requests.at(-1)?.searchParams.get("until")).toBe(until);
});

test("audit applies explicit filters, handles empty results and recovers invalid links without retry loops", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installAudit(page);
  await page.goto(`${path}?from=invalid`);
  await expect(page.getByRole("alert")).toContainText("Choose a valid UTC time window");
  expect(api.requests).toEqual([]);
  await page.getByRole("button", { name: "Reset filters", exact: true }).click();
  await expect(page.getByRole("table", { name: "Events", exact: true })).toBeVisible();
  const loaded = api.requests.length;
  await page.getByLabel("Action code", { exact: true }).fill("employment.updated");
  expect(api.requests).toHaveLength(loaded);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("No events match these filters.");
  await page.goBack();
  await expect(page.getByLabel("Action code", { exact: true })).toHaveValue("");
  await expect(page.getByRole("table", { name: "Events", exact: true })).toBeVisible();
  await page.getByLabel("Action code", { exact: true }).fill("unfinished.edit");
  await page.getByRole("button", { name: "Reset filters", exact: true }).click();
  await expect(page.getByLabel("Action code", { exact: true })).toHaveValue("");
  await page.getByLabel("From (UTC)", { exact: true }).fill("2026-09-10T07:30");
  await page.getByLabel("Until (UTC)", { exact: true }).fill("2026-10-01T07:30");
  await page.getByLabel("Resource type", { exact: true }).fill("employment");
  await page.getByLabel("Resource ID", { exact: true }).fill(resourceId);
  const filteredResponse = page.waitForResponse((result) => {
    const url = new URL(result.url());
    return (
      result.request().method() === "GET" &&
      url.pathname === `/api/v1/companies/${companyIds[0]}/audit-events` &&
      url.searchParams.get("from") === "2026-09-10T07:30:00Z"
    );
  });
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  const filtered = await filteredResponse;
  expect(filtered.status()).toBe(200);
  expect(Object.fromEntries(new URL(filtered.url()).searchParams)).toEqual({
    from: "2026-09-10T07:30:00Z",
    until: "2026-10-01T07:30:00Z",
    resourceType: "employment",
    resourceId,
    limit: "50",
  });
  await expect(page.getByRole("table", { name: "Events", exact: true })).toBeVisible();
  api.fail("invalid_audit_cursor");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Reset the filters to start again.");
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE AUDIT FAILURE");
  const failed = api.requests.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByRole("alert")).toContainText("Reset filter untuk memulai kembali.");
  expect(api.requests).toHaveLength(failed);
  api.fail(null);
  await page.getByRole("button", { name: "Reset filter", exact: true }).click();
  await expect(page.getByRole("table", { name: "Event", exact: true })).toBeVisible();
});

test("company switches drop foreign cursors and late audit responses, then logout removes open details", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  let enter!: () => void;
  let release!: () => void;
  const entered = new Promise<void>((resolve) => {
    enter = resolve;
  });
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/v1/companies/*/audit-events?*", async (route) => {
    const url = new URL(route.request().url());
    const company = url.pathname.split("/")[4] ?? "";
    if (company === companyIds[0]) {
      enter();
      await held;
    } else expect(url.searchParams.has("cursor")).toBe(false);
    await route.fulfill({ json: response(company, url) });
  });
  try {
    const parameters = new URLSearchParams({
      company: companyIds[0],
      from,
      until,
      cursor: eventId(51),
    });
    await page.goto(`${path}?${parameters}`);
    await entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Events", exact: true })).toBeVisible();
    await expect(page.getByRole("main")).toContainText("South Company");
    release();
    await page
      .getByRole("button", { name: /^View details:/u })
      .first()
      .click();
    const dialog = page.getByRole("dialog", { name: "Audit event", exact: true });
    await expect(dialog).toContainText(companyIds[1]);
    await expect(dialog).not.toContainText(companyIds[0]);
    await page.getByRole("button", { name: "Close", exact: true }).click();
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
    await expect(page.getByRole("table")).toHaveCount(0);
    await expect(dialog).toHaveCount(0);
  } finally {
    release();
  }
});

test("audit permission is required on direct links and revoked access clears loaded metadata", async ({
  page,
}) => {
  const focusErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error" && message.text().includes("Keyborg"))
      focusErrors.push(message.text());
  });
  await installIdentityApi(page, { signedIn: true, permissions: ["company.read"] });
  const api = await installAudit(page);
  await page.goto(path);
  await expect(page.getByRole("link", { name: "Audit log", exact: true })).toHaveCount(0);
  await expect(page.getByRole("alert")).toContainText("You do not have access to this resource.");
  expect(api.requests).toEqual([]);
  await installIdentityApi(page, { signedIn: true, permissions });
  await installAudit(page);
  await page.reload();
  await expect(page.getByRole("table", { name: "Events", exact: true })).toBeVisible();
  await page.route("**/api/v1/companies/*/audit-events?*", (route) =>
    route.fulfill({
      status: 403,
      json: { code: "company_access_denied", fields: {}, parameters: {} },
    }),
  );
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Company access is unavailable.");
  await expect(page.getByRole("table")).toHaveCount(0);
  expect(focusErrors).toEqual([]);
});

test("Fluent focus ownership survives repeated panel, route and company changes", async ({
  page,
}) => {
  const focusErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error" && message.text().includes("Keyborg"))
      focusErrors.push(message.text());
  });
  await installIdentityApi(page, { signedIn: true, permissions });
  await installAudit(page);
  await page.goto(path);
  for (const company of [companyIds[1], companyIds[0], companyIds[1]]) {
    const table = page.getByRole("table", { name: "Events", exact: true });
    await expect(table).toBeVisible();
    await table
      .getByRole("button", { name: /^View details:/u })
      .first()
      .click();
    await expect(page.getByRole("dialog", { name: "Audit event", exact: true })).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
    await expect(table).toBeVisible();
    await page.getByRole("link", { name: "Overview", exact: true }).click();
    await expect(table).toHaveCount(0);
    await page.getByRole("link", { name: "Audit log", exact: true }).click();
  }
  await expect(page.getByRole("table", { name: "Events", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
  expect(focusErrors).toEqual([]);
});
