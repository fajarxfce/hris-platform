import type { Page } from "@playwright/test";
import { detail, summary } from "../src/features/communications/di/communications-fixture";
import { companyIds, installIdentityApi } from "./identity-api";

export const announcementId = "21000000-0000-4000-8000-000000000001";
export async function installAnnouncementsApi(
  page: Page,
  options: { allowed?: boolean; longPage?: boolean } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    permissions: options.allowed === false ? [] : ["announcements.manage"],
  });
  const reads: URL[] = [];
  let failure: string | null = null;
  let hold: { entered: () => void; wait: Promise<void> } | null = null;
  await page.route("**/api/v1/companies/*/announcements**", async (route) => {
    const url = new URL(route.request().url());
    const match =
      /^\/api\/v1\/companies\/([^/]+)\/announcements(?:\/([^/]+))?(?:\/(history|revisions)(?:\/(\d+))?)?$/u.exec(
        url.pathname,
      );
    if (!match) return route.fallback();
    reads.push(url);
    const paused = hold;
    hold = null;
    if (paused) {
      paused.entered();
      await paused.wait;
    }
    if (failure)
      return route.fulfill({
        status: 404,
        json: { code: failure, detail: "PRIVATE SERVER DETAIL" },
      });
    if (match[1] !== companyIds[0]) {
      if (match[2]) return route.fulfill({ status: 404, json: { code: "announcement_not_found" } });
      return route.fulfill({ json: { items: [], nextCursor: null } });
    }
    const subject = "Office closure";
    if (match[3] === "history")
      return route.fulfill({
        json: { items: [summary(0), summary(1), summary(2)], nextCursor: null },
      });
    if (match[2]) {
      if (match[2] !== announcementId)
        return route.fulfill({ status: 404, json: { code: "announcement_not_found" } });
      const version = match[4] ? Number(match[4]) : 2;
      return route.fulfill({
        json: {
          ...detail(version),
          body:
            version === 0
              ? "Original office notice."
              : "The office will be closed on Friday.\n<script>Private HTML is plain text.</script>",
        },
      });
    }
    const all = Array.from({ length: options.longPage ? 51 : 1 }, (_, index) => ({
      ...summary(2),
      id: `21000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
      title: index === 0 ? subject : `Notice ${index + 1}`,
    }));
    const after = url.searchParams.get("after");
    const filtered = all.filter((item) => after === null || item.id > after);
    const items = filtered.slice(0, 50);
    return route.fulfill({
      json: { items, nextCursor: filtered.length > 50 ? items.at(-1)?.id : null },
    });
  });
  return {
    identity,
    reads,
    fail: (code: string) => {
      failure = code;
    },
    holdNextRead: () => {
      let entered!: () => void;
      let release!: () => void;
      const start = new Promise<void>((resolve) => {
        entered = resolve;
      });
      const wait = new Promise<void>((resolve) => {
        release = resolve;
      });
      hold = { entered, wait };
      return { start, release };
    },
  };
}
