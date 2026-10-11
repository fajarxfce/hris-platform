import { expect, type Page } from "@playwright/test";
import type { AnnouncementChangeDto } from "../src/features/communications/data/models/announcement-change-dto";
import type { AnnouncementDto } from "../src/features/communications/data/models/announcement-dto";
import { detail } from "../src/features/communications/di/communications-fixture";
import { announcementId } from "./announcements-api";
import { companyIds, installIdentityApi } from "./identity-api";

export async function installAnnouncementEditorApi(page: Page, allowed = true) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfaConfigured: true,
    permissions: allowed ? ["announcements.manage"] : [],
  });
  const records = new Map<string, AnnouncementDto>([[announcementId, detail(0)]]);
  const writes: { operation: string | undefined; path: string; body: AnnouncementChangeDto }[] = [];
  const reads: URL[] = [];
  const lookups: URL[] = [];
  const receipts = new Map<string, { payload: string; id: string; version: number }>();
  let commits = 0;
  let drop = false;
  let rejection: string | null = null;
  let hold: { entered: () => void; wait: Promise<void> } | null = null;
  await page.route("**/api/v1/companies/*/communications/audience-references?*", async (route) => {
    const url = new URL(route.request().url());
    lookups.push(url);
    const pending = hold;
    hold = null;
    if (pending) {
      pending.entered();
      await pending.wait;
    }
    if (!url.pathname.includes(companyIds[0]))
      return route.fulfill({ json: { items: [], nextCursor: null } });
    const kind = url.searchParams.get("kind");
    const all = Array.from({ length: 51 }, (_, index) => ({
      id: `51000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
      kind,
      name: index === 0 ? "South office" : `Office ${index + 1}`,
      code: kind === "GROUP" ? null : `OFFICE-${index + 1}`,
      version: 0,
      active: kind === "EMPLOYMENT" ? null : true,
    }));
    const ids = url.searchParams.getAll("ids");
    const text = url.searchParams.get("query")?.toLowerCase() ?? "";
    const after = url.searchParams.get("after");
    const selected = all.filter((item) =>
      ids.length
        ? ids.includes(item.id)
        : (!after || item.id > after) &&
          (item.name.toLowerCase().includes(text) || item.code?.toLowerCase().includes(text)),
    );
    const items = selected.slice(0, 50);
    return route.fulfill({
      json: { items, nextCursor: selected.length > 50 ? items.at(-1)?.id : null },
    });
  });
  await page.route("**/api/v1/companies/*/announcements**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const match = /^\/api\/v1\/companies\/([^/]+)\/announcements(?:\/([^/]+))?$/u.exec(
      url.pathname,
    );
    if (!match) return route.fallback();
    const id = match[2];
    if (request.method() === "GET") {
      reads.push(url);
      if (id) {
        const record = match[1] === companyIds[0] ? records.get(id) : undefined;
        return record
          ? route.fulfill({ json: record })
          : route.fulfill({ status: 404, json: { code: "announcement_not_found" } });
      }
      const items =
        match[1] === companyIds[0]
          ? [...records.values()]
              .sort((a, b) => a.id.localeCompare(b.id))
              .map((item) => ({
                ...item,
                audienceKind: item.audience.kind,
                targetCount: item.audience.targetIds.length,
              }))
          : [];
      return route.fulfill({ json: { items, nextCursor: null } });
    }
    expect(request.method()).toBe("PUT");
    expect(id).toBeTruthy();
    const operation = request.headers()["idempotency-key"];
    const body = request.postDataJSON() as AnnouncementChangeDto;
    writes.push({ operation, path: url.pathname, body });
    expect(request.headers()["x-csrf-token"]).toMatch(/^fixture-csrf-/u);
    if (rejection) {
      const code = rejection;
      rejection = null;
      return route.fulfill({ status: code === "mfa_required" ? 403 : 409, json: { code } });
    }
    const payload = JSON.stringify([url.pathname, body]);
    let receipt = receipts.get(operation ?? "");
    if (receipt && receipt.payload !== payload)
      return route.fulfill({ status: 409, json: { code: "operation_payload_mismatch" } });
    if (!receipt) {
      const previous = records.get(id ?? "");
      if ((previous?.version ?? null) !== body.expectedVersion)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      const record: AnnouncementDto = {
        ...detail(0),
        ...previous,
        id: id ?? "",
        version: (previous?.version ?? -1) + 1,
        title: body.title,
        body: body.body,
        audience: {
          kind: body.audience.kind as AnnouncementDto["audience"]["kind"],
          targetIds: [...body.audience.targetIds],
        },
        acknowledgementRequired: body.acknowledgementRequired,
        reason: body.reason,
      };
      records.set(record.id, record);
      receipt = { id: record.id, version: record.version, payload };
      receipts.set(operation ?? "", receipt);
      commits++;
    }
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id: receipt.id, version: receipt.version } });
  });
  return {
    identity,
    writes,
    reads,
    lookups,
    get commits() {
      return commits;
    },
    dropNext: () => {
      drop = true;
    },
    rejectNext: (code: string) => {
      rejection = code;
    },
    revise: () => {
      const current = records.get(announcementId);
      if (current)
        records.set(announcementId, {
          ...current,
          title: "Reviewed office hours",
          version: current.version + 1,
        });
    },
    holdNextLookup: () => {
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
