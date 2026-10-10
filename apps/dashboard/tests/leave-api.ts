import { expect, type Page } from "@playwright/test";
import type { LeaveRequestDetailsDto } from "../src/features/leave/data/models/leave-request-details-dto";
import { leaveRecord, leaveSummary, leaveWithHistory } from "./fixtures/leave";
import { companyIds, installIdentityApi } from "./identity-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installLeaveApi(
  page: Page,
  permissions = ["leave.read", "approvals.read", "approvals.manage", "leave.approve"],
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const records = new Map<string, LeaveRequestDetailsDto[]>(
    companyIds.map((company, index) => [
      company,
      Array.from({ length: index === 0 ? 22 : 1 }, (_, number) =>
        index === 0 && number === 0 ? leaveWithHistory() : leaveRecord(index, number + 1),
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
  await page.route("**/api/v1/companies/*/leave/requests**", async (route) => {
    expect(route.request().method()).toBe("GET");
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
    if (failure)
      return route.fulfill({
        status: 404,
        json: { code: failure, detail: "PRIVATE TECHNICAL MESSAGE", fields: {}, parameters: {} },
      });
    const all = records.get(company) ?? [];
    if (id) {
      expect(url.searchParams.get("historyLimit")).toBe("20");
      const record = all.find((value) => value.id === id);
      if (!record) return route.fulfill({ status: 404, json: { code: "leave_request_not_found" } });
      const after = url.searchParams.get("historyAfter");
      const changes = record.history.items.filter(
        (change) => after === null || change.version < Number(after),
      );
      const reply = {
        ...record,
        history: {
          items: changes.slice(0, 20),
          nextCursor: changes.length > 20 ? String(changes[19]?.version) : null,
        },
      };
      return route.fulfill({
        json: malformed ? { ...reply, days: [...reply.days, ...reply.days] } : reply,
      });
    }
    expect(url.searchParams.get("limit")).toBe("20");
    const employee = url.searchParams.get("employeeId");
    const status = url.searchParams.get("status");
    const after = url.searchParams.get("after");
    const start = after === null ? 0 : all.findIndex((value) => value.id === after) + 1;
    const available = all
      .slice(start)
      .filter(
        (value) =>
          (employee === null || value.employeeId === employee) &&
          (status === null || value.status === status),
      );
    const items = available.slice(0, 20).map(leaveSummary);
    return route.fulfill({
      json: {
        items: malformed && items.length ? [items[0], items[0]] : items,
        nextCursor: available.length > 20 ? items[19]?.id : null,
      },
    });
  });
  await page.route("**/api/v1/companies/*/approvals**", async (route) => {
    const url = new URL(route.request().url());
    const parts = url.pathname.split("/");
    const company = parts[4] ?? "";
    const id = parts[6];
    const requests = (records.get(company) ?? [])
      .flatMap((record) => [
        { record, workflow: record.approval, kind: "LEAVE" },
        ...(record.cancellation
          ? [{ record, workflow: record.cancellation, kind: "LEAVE_CANCELLATION" }]
          : []),
      ])
      .map(({ record, workflow, kind }) => ({
        ...workflow,
        kind,
        resourceId: record.id,
        templateId: "b7000000-0000-4000-8000-000000000001",
        templateRevision: 2,
        stages: workflow.stages.map((assignees) => ({ assignees })),
        submittedAt: record.submittedAt,
        excludedAccountIds: [],
      }));
    if (id) {
      const request = requests.find((value) => value.id === id);
      return route.fulfill(
        request ? { json: request } : { status: 404, json: { code: "approval_not_found" } },
      );
    }
    const after = url.searchParams.get("after");
    const pending = requests
      .filter(
        (value) =>
          ["PENDING", "BLOCKED"].includes(value.status) && (after === null || value.id > after),
      )
      .sort((a, b) => a.id.localeCompare(b.id));
    return route.fulfill({
      json: {
        items: pending.slice(0, 20),
        nextCursor: pending.length > 20 ? pending[19]?.id : null,
      },
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
    hold: (kind: "list" | "details") => {
      const entered = gate();
      const released = gate();
      held = { kind, entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
  };
}
