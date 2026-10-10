import { expect, type Page } from "@playwright/test";
import type { ApprovalRequestDto } from "../src/features/approvals/data/models/approval-request-dto";
import { companyIds, installIdentityApi } from "./identity-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

export const approvalId = (company: number, index: number) =>
  `30000000-0000-4000-8000-${String(company * 100 + index).padStart(12, "0")}`;
export async function installApprovalsApi(
  page: Page,
  permissions = ["approvals.read", "approvals.manage", "leave.approve"],
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const records = new Map<string, ApprovalRequestDto[]>(
    companyIds.map((company, companyIndex) => [
      company,
      Array.from({ length: companyIndex === 0 ? 22 : 1 }, (_, index) => ({
        id: approvalId(companyIndex, index + 1),
        kind: "LEAVE",
        resourceId: `40000000-0000-4000-8000-${String(companyIndex * 100 + index + 1).padStart(12, "0")}`,
        authorId: "20000000-0000-4000-8000-000000000099",
        requesterId: null,
        templateId: "50000000-0000-4000-8000-000000000001",
        templateRevision: 0,
        stages: [
          { assignees: ["20000000-0000-4000-8000-000000000002"] },
          { assignees: index === 1 ? [] : ["20000000-0000-4000-8000-000000000001"] },
        ],
        currentStep: 1,
        status: index === 1 ? "BLOCKED" : "PENDING",
        version: 1,
        submittedAt: "2026-10-01T09:30:00.123456Z",
        excludedAccountIds: [],
      })),
    ]),
  );
  const reads: URL[] = [];
  let failure: string | null = null;
  let malformed = false;
  let held: {
    kind: "inbox" | "request";
    entered: ReturnType<typeof gate>;
    released: ReturnType<typeof gate>;
  } | null = null;
  await page.route("**/api/v1/companies/*/approvals**", async (route) => {
    expect(route.request().method()).toBe("GET");
    const url = new URL(route.request().url());
    reads.push(url);
    const parts = url.pathname.split("/");
    const company = parts[4] ?? "";
    const id = parts[6];
    const kind = id ? "request" : "inbox";
    const hold = held;
    if (hold?.kind === kind) {
      held = null;
      hold.entered.resolve();
      await hold.released.promise;
    }
    if (failure)
      return route.fulfill({
        status: 403,
        json: { code: failure, fields: {}, parameters: {}, detail: "PRIVATE TECHNICAL MESSAGE" },
      });
    const all = records.get(company) ?? [];
    if (id) {
      const value = all.find((record) => record.id === id);
      if (!value)
        return route.fulfill({
          status: 404,
          json: { code: "approval_not_found", fields: {}, parameters: {} },
        });
      return route.fulfill({ json: malformed ? { ...value, currentStep: 8 } : value });
    }
    expect(url.searchParams.get("limit")).toBe("20");
    const after = url.searchParams.get("after");
    const items = all.filter((record) => after === null || record.id > after).slice(0, 21);
    const nextCursor = items.length > 20 ? (items[19]?.id ?? null) : null;
    return route.fulfill({
      json: { items: malformed ? [all[0], all[0]] : items.slice(0, 20), nextCursor },
    });
  });
  return {
    identity,
    records,
    reads,
    fail: (code: string | null) => {
      failure = code;
    },
    malformed: () => {
      malformed = true;
    },
    hold: (kind: "inbox" | "request") => {
      const entered = gate();
      const released = gate();
      held = { kind, entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
  };
}
