import { expect, type Page, test } from "@playwright/test";
import { installIdentityApi } from "./identity-api";

const path = "/platform/audit";
const id = (value: number) => `40000000-0000-4000-8000-${String(value).padStart(12, "0")}`;
const event = (value: number) => ({
  id: id(value),
  actorId: "20000000-0000-4000-8000-000000000001",
  resourceType: "account",
  resourceId: "30000000-0000-4000-8000-000000000001",
  action: "identity.access_changed",
  correlationId: "50000000-0000-4000-8000-000000000001",
  recordedAt: "2026-10-25T00:00:00Z",
});
async function fixture(page: Page, auditor = true) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfaConfigured: true,
    noCompanies: auditor,
    platformPermissions: auditor ? ["audit.read"] : [],
    permissions: ["company.read", "audit.read"],
  });
  const reads: URL[] = [];
  let failure: string | null = null;
  await page.route("**/api/v1/administration/audit-events?*", (route) => {
    const url = new URL(route.request().url());
    reads.push(url);
    if (failure)
      return route.fulfill({ status: 403, json: { code: failure, detail: "PRIVATE_DETAIL" } });
    return route.fulfill({
      json: {
        from: "2026-10-01T00:00:00Z",
        until: "2026-10-31T00:00:00Z",
        evaluatedAt: "2026-10-31T00:00:00Z",
        items: url.searchParams.has("cursor")
          ? [event(1)]
          : Array.from({ length: 50 }, (_, index) => event(100 - index)),
        nextCursor: url.searchParams.has("cursor") ? null : id(51),
      },
    });
  });
  return {
    identity,
    reads,
    fail: (code: string) => {
      failure = code;
    },
  };
}

test("global auditor without companies opens metadata and continues the same search window", async ({
  page,
}) => {
  const f = await fixture(page);
  await page.goto(path);
  await expect(page.getByRole("heading", { name: "Platform audit", exact: true })).toBeVisible();
  await expect(
    page.getByRole("navigation").getByRole("link", { name: "Platform audit", exact: true }),
  ).toHaveAttribute("aria-current", "page");
  const table = page.getByRole("table", { name: "Events", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await page
    .getByRole("button", { name: /^View details:/u })
    .first()
    .click();
  await expect(page.getByText(event(100).correlationId, { exact: true })).toBeVisible();
  await expect(page.getByText("Company ID", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("button", { name: "Older events", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  expect(f.reads.at(-1)?.searchParams.get("cursor")).toBe(id(51));
  expect(f.reads.at(-1)?.searchParams.get("until")).toBe("2026-10-31T00:00:00Z");
  await page.screenshot({ path: "../../.work/platform-audit-desktop.png", fullPage: true });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("heading", { name: "Audit platform", exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: "../../.work/platform-audit-narrow.png", fullPage: true });
  expect(f.identity.unhandled).toEqual([]);
});

test("company audit permission never grants global navigation or performs a global read", async ({
  page,
}) => {
  const f = await fixture(page, false);
  await page.goto(path);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(
    page.getByRole("navigation").getByRole("link", { name: "Platform audit", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole("table", { name: "Events", exact: true })).toHaveCount(0);
  expect(f.reads).toEqual([]);
  expect(f.identity.unhandled).toEqual([]);
});

test("failed refresh clears open metadata and company cursors cannot be reused globally", async ({
  page,
}) => {
  const f = await fixture(page);
  await page.goto(`${path}?company=${id(12)}&cursor=${id(2)}`);
  await expect(page.getByRole("table", { name: "Events", exact: true })).toBeVisible();
  expect(f.reads[0]?.searchParams.has("cursor")).toBe(false);
  f.fail("connection_unavailable");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("table", { name: "Events", exact: true })).toHaveCount(0);
  await expect(page.getByText("PRIVATE_DETAIL")).toHaveCount(0);
  expect(f.reads).toHaveLength(2);
});
