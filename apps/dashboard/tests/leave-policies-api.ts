import { expect, type Page } from "@playwright/test";
import type { LeavePolicyReviewDto } from "../src/features/leave/data/models/leave-type-dto";
import { leavePolicyRecord, leavePolicyReviewPage } from "./fixtures/leave-policies";
import { companyIds, installIdentityApi } from "./identity-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

export async function installLeavePoliciesApi(page: Page, permissions = ["leave.manage"]) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const records = new Map<string, LeavePolicyReviewDto[]>(
    companyIds.map((company, index) => [
      company,
      Array.from({ length: index === 0 ? 23 : 1 }, (_, number) =>
        leavePolicyRecord(index, number + 1, index === 0 && number === 0 ? 23 : 0),
      ),
    ]),
  );
  const reads: URL[] = [];
  let failure: string | null = null;
  let malformed = false;
  let held: {
    kind: "list" | "details";
    entered: ReturnType<typeof gate>;
    released: ReturnType<typeof gate>;
  } | null = null;
  await page.route("**/api/v1/companies/*/leave/policies**", async (route) => {
    expect(route.request().method()).toBe("GET");
    expect(route.request().headers()["x-hris-client-platform"]).toBe("WEB");
    const url = new URL(route.request().url());
    reads.push(url);
    const parts = url.pathname.split("/");
    const company = parts[4] ?? "";
    const id = parts[7];
    const kind = id ? "details" : "list";
    const hold = held;
    if (hold?.kind === kind) {
      held = null;
      hold.entered.resolve();
      await hold.released.promise;
    }
    if (failure) {
      const code = failure;
      failure = null;
      return route.fulfill({
        status: code === "leave_type_not_found" ? 404 : 403,
        json: { code, detail: "PRIVATE TECHNICAL MESSAGE", fields: {}, parameters: {} },
      });
    }
    const all = records.get(company) ?? [];
    if (id) {
      expect(url.searchParams.get("historyLimit")).toBe("20");
      const record = all.find((value) => value.current.id === id);
      if (!record) return route.fulfill({ status: 404, json: { code: "leave_type_not_found" } });
      const after = url.searchParams.get("historyAfter");
      const reply = leavePolicyReviewPage(record, after === null ? null : Number(after));
      return route.fulfill({
        json: malformed ? { ...reply, current: { ...reply.current, appliedRevision: -1 } } : reply,
      });
    }
    expect(url.searchParams.get("limit")).toBe("20");
    const active = url.searchParams.get("active");
    const after = url.searchParams.get("after");
    const start = after === null ? 0 : all.findIndex((value) => value.current.code === after) + 1;
    const available = all
      .slice(start)
      .filter((record) => active === null || record.current.active === (active === "true"));
    const items = available.slice(0, 20).map((record) => record.current);
    return route.fulfill({
      json: {
        items: malformed && items.length ? [items[0], items[0]] : items,
        nextCursor: available.length > 20 ? items[19]?.code : null,
      },
    });
  });
  return {
    identity,
    records,
    reads,
    failNext: (code: string) => {
      failure = code;
    },
    malformed: () => {
      malformed = true;
    },
    hold: (kind: "list" | "details") => {
      const entered = gate();
      const released = gate();
      held = { kind, entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
  };
}
