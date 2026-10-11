import { expect, type Page } from "@playwright/test";
import type { AudienceGroupChangeDto } from "../src/features/communications/data/models/audience-group-change-dto";
import type { AudienceGroupDto } from "../src/features/communications/data/models/audience-group-dto";
import {
  employeeId,
  groupDetail,
  groupId,
} from "../src/features/communications/di/audience-group-fixture";
import { companyIds, installIdentityApi } from "./identity-api";

export async function installAudienceGroupsApi(page: Page, allowed = true) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfaConfigured: true,
    permissions: allowed ? ["announcements.manage"] : [],
  });
  const employees = Array.from({ length: 51 }, (_, index) => ({
    id: employeeId(index + 1),
    kind: "EMPLOYMENT",
    name: `Fictional Employee ${index + 1}`,
    code: `EMP-${index + 1}`,
    version: 0,
    active: null,
  }));
  const groups = new Map<string, AudienceGroupDto[]>();
  for (let index = 0; index < 51; index++) {
    const id = `61000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`;
    groups.set(
      id,
      index === 0
        ? [groupDetail(0), { ...groupDetail(1), employmentIds: employees.map((item) => item.id) }]
        : [{ ...groupDetail(0), id, name: `Team ${index + 1}`, employmentIds: [] }],
    );
  }
  const writes: { operation: string | undefined; body: AudienceGroupChangeDto; id: string }[] = [];
  const reads: URL[] = [];
  const lookups: URL[] = [];
  const receipts = new Map<string, { id: string; version: number; payload: string }>();
  let drop = false;
  let reject: string | null = null;
  let commits = 0;
  let hold: { entered: () => void; wait: Promise<void> } | null = null;
  await page.route("**/api/v1/companies/*/communications/audience-references?*", async (route) => {
    const url = new URL(route.request().url());
    lookups.push(url);
    expect(url.searchParams.get("kind")).toBe("EMPLOYMENT");
    const ids = url.searchParams.getAll("ids");
    expect(ids.length).toBeLessThanOrEqual(50);
    const query = url.searchParams.get("query")?.toLowerCase() ?? "";
    const after = url.searchParams.get("after");
    const filtered = url.pathname.includes(companyIds[0])
      ? employees.filter((item) =>
          ids.length
            ? ids.includes(item.id)
            : (!after || item.id > after) &&
              (item.name.toLowerCase().includes(query) || item.code.toLowerCase().includes(query)),
        )
      : [];
    const items = filtered.slice(0, 50);
    return route.fulfill({
      json: { items, nextCursor: filtered.length > 50 ? items.at(-1)?.id : null },
    });
  });
  await page.route("**/api/v1/companies/*/communications/audience-groups**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const match =
      /^\/api\/v1\/companies\/([^/]+)\/communications\/audience-groups(?:\/([^/]+)(?:\/revisions\/([0-9]+))?)?$/u.exec(
        url.pathname,
      );
    if (!match) return route.fallback();
    const id = match[2];
    if (request.method() === "GET") {
      reads.push(url);
      const pending = hold;
      if (pending) {
        pending.entered();
        await pending.wait;
      }
      if (id) {
        const versions = match[1] === companyIds[0] ? groups.get(id) : undefined;
        const result = match[3]
          ? versions?.find((item) => item.version === Number(match[3]))
          : versions?.at(-1);
        return result
          ? route.fulfill({ json: result })
          : route.fulfill({ status: 404, json: { code: "audience_group_not_found" } });
      }
      const after = url.searchParams.get("after");
      const all =
        match[1] === companyIds[0]
          ? [...groups.values()]
              .flatMap((versions) => {
                const latest = versions.at(-1);
                return latest && (!after || latest.id > after)
                  ? [{ ...latest, memberCount: latest.employmentIds.length }]
                  : [];
              })
              .sort((a, b) => a.id.localeCompare(b.id))
          : [];
      const items = all.slice(0, 50);
      return route.fulfill({
        json: { items, nextCursor: all.length > 50 ? items.at(-1)?.id : null },
      });
    }
    expect(request.method()).toBe("PUT");
    if (!id) throw new Error("Missing group identifier");
    const body = request.postDataJSON() as AudienceGroupChangeDto;
    const operation = request.headers()["idempotency-key"];
    writes.push({ id, operation, body });
    expect(request.headers()["x-csrf-token"]).toMatch(/^fixture-csrf-/u);
    if (reject) {
      const code = reject;
      reject = null;
      return route.fulfill({ status: code === "mfa_required" ? 403 : 409, json: { code } });
    }
    const payload = JSON.stringify([url.pathname, body]);
    let receipt = receipts.get(operation ?? "");
    if (receipt && receipt.payload !== payload)
      return route.fulfill({ status: 409, json: { code: "operation_payload_mismatch" } });
    if (!receipt) {
      const versions = groups.get(id) ?? [];
      const previous = versions.at(-1);
      if ((previous?.version ?? null) !== body.expectedVersion)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      const current = { ...groupDetail(), ...body, id, version: (previous?.version ?? -1) + 1 };
      groups.set(id, [...versions, current]);
      receipt = { id, version: current.version, payload };
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
    reads,
    writes,
    lookups,
    get commits() {
      return commits;
    },
    dropNext: () => {
      drop = true;
    },
    rejectNext: (code: string) => {
      reject = code;
    },
    revise: () => {
      const versions = groups.get(groupId) ?? [];
      const current = versions.at(-1);
      if (current)
        groups.set(groupId, [
          ...versions,
          { ...current, version: current.version + 1, name: "Reviewed team", active: false },
        ]);
    },
    holdReads: () => {
      let entered!: () => void;
      let release!: () => void;
      const start = new Promise<void>((resolve) => {
        entered = resolve;
      });
      const wait = new Promise<void>((resolve) => {
        release = resolve;
      });
      hold = { entered, wait };
      return {
        start,
        release: () => {
          hold = null;
          release();
        },
      };
    },
  };
}
