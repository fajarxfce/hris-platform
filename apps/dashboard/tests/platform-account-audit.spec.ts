import { expect, type Page, test } from "@playwright/test";
import { installIdentityApi } from "./identity-api";

const event = {
  id: "40000000-0000-4000-8000-000000000001",
  actorId: "20000000-0000-4000-8000-000000000001",
  resourceType: "account",
  resourceId: "30000000-0000-4000-8000-000000000001",
  action: "identity.account_access_saved",
  correlationId: "50000000-0000-4000-8000-000000000001",
  recordedAt: "2026-10-25T00:00:00Z",
};
async function fixture(page: Page, permissions: string[], supported = true) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfaConfigured: true,
    noCompanies: true,
    platformPermissions: permissions,
  });
  await page.route("**/api/v1/administration/audit-events?*", (route) =>
    route.fulfill({
      json: {
        from: "2026-10-01T00:00:00Z",
        until: "2026-10-31T00:00:00Z",
        evaluatedAt: "2026-10-31T00:00:00Z",
        items: [{ ...event, action: supported ? event.action : "identity.other" }],
        nextCursor: null,
      },
    }),
  );
  return identity;
}

test("typed saved access retries safely, renders text in both locales and clears when closed", async ({
  page,
}) => {
  const identity = await fixture(page, ["audit.read", "identity.manage"]);
  let reads = 0;
  await page.route("**/api/v1/administration/audit-events/*/account-access", (route) => {
    reads++;
    return reads === 1
      ? route.fulfill({
          status: 503,
          json: { code: "connection_unavailable", detail: "PRIVATE_ERROR" },
        })
      : route.fulfill({
          json: {
            id: event.id,
            accountId: event.resourceId,
            active: false,
            permissions: ["audit.read"],
            reason: "Saved <script>reason</script>",
          },
        });
  });
  await page.goto("/platform/audit");
  await page.getByRole("button", { name: /^View details:/u }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByText("PRIVATE_ERROR")).toHaveCount(0);
  await page.getByRole("button", { name: "Retry", exact: true }).click();
  await expect(page.getByText("Saved <script>reason</script>", { exact: true })).toBeVisible();
  await expect(page.getByText("Inactive", { exact: true })).toBeVisible();
  await expect(page.locator(".app-details-panel script")).toHaveCount(0);
  await page.getByText("Saved <script>reason</script>", { exact: true }).scrollIntoViewIfNeeded();
  await page.screenshot({ path: "../../.work/account-audit-desktop.png", fullPage: true });
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: /^Lihat detail:/u }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("heading", { name: "Akses akun tersimpan", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("Nonaktif", { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByText("Saved <script>reason</script>", { exact: true }).scrollIntoViewIfNeeded();
  await page.screenshot({ path: "../../.work/account-audit-narrow.png", fullPage: true });
  expect(reads).toBe(3);
  await page.getByRole("button", { name: "Tutup", exact: true }).click();
  await expect(page.getByText("Saved <script>reason</script>")).toHaveCount(0);
  expect(identity.unhandled).toEqual([]);
});

test("metadata-only authority or another source family never requests private account details", async ({
  page,
}) => {
  for (const supported of [true, false]) {
    const identity = await fixture(
      page,
      supported ? ["audit.read"] : ["audit.read", "identity.manage"],
      supported,
    );
    let reads = 0;
    await page.route("**/api/v1/administration/audit-events/*/account-access", (route) => {
      reads++;
      return route.abort();
    });
    await page.goto("/platform/audit");
    await page.getByRole("button", { name: /^View details:/u }).click();
    await expect(page.getByRole("heading", { name: "Event metadata", exact: true })).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Saved account access", exact: true }),
    ).toHaveCount(0);
    expect(reads).toBe(0);
    expect(identity.unhandled).toEqual([]);
    await page.unrouteAll({ behavior: "wait" });
  }
});
