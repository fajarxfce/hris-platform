import { expect, type Page } from "@playwright/test";
import type { LifecycleCaseDto } from "../src/features/lifecycle/data/models/lifecycle-case-dto";
import type { LifecycleHistoryPageDto } from "../src/features/lifecycle/data/models/lifecycle-event-dto";
import { companyIds, installIdentityApi } from "./identity-api";

export const lifecycleAccountId = "20000000-0000-4000-8000-000000000001";
export const lifecycleCaseId = (company = 0, n = 1) =>
  `a0000000-abcd-4000-8000-${company + 1}${String(n).padStart(11, "0")}`;
export function lifecycleCaseSeed(company: number, n: number): LifecycleCaseDto {
  const employmentId = `b0000000-abcd-4000-8000-${company + 1}${String(n).padStart(11, "0")}`;
  return {
    id: lifecycleCaseId(company, n),
    employmentId,
    employee: {
      id: employmentId,
      employeeNumber: `E${String(n).padStart(3, "0")}`,
      name: `${company === 0 ? "North" : "South"} employee ${n}`,
    },
    kind: n % 2 === 0 ? "OFFBOARDING" : "ONBOARDING",
    targetDate: "2026-10-01",
    templateId: "c0000000-abcd-4000-8000-000000000001",
    templateVersion: 0,
    templateName: "Standard checklist",
    status: "OPEN",
    version: n === 1 ? 50 : 2,
    createdBy: lifecycleAccountId,
    createdAt: "2026-10-01T00:00:00Z",
    tasks: [
      { key: "access", title: "Review access", required: true },
      { key: "equipment", title: "Review equipment", required: true },
      { key: "welcome", title: "Welcome session", required: false },
    ].map((task) => ({
      ...task,
      dueDate: "2026-10-02",
      assigneeId: lifecycleAccountId,
      status: "PENDING",
      completedBy: null,
      completedAt: null,
    })),
  };
}
function gate() {
  let enter!: () => void;
  let release!: () => void;
  const entered = new Promise<void>((resolve) => {
    enter = resolve;
  });
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  return { entered, held, enter, release };
}

export async function installLifecycleCasesApi(
  page: Page,
  options: { permissions?: string[]; empty?: boolean } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions: options.permissions ?? ["people.lifecycle.read", "people.lifecycle.perform"],
  });
  const records = new Map<string, LifecycleCaseDto[]>(
    companyIds.map((company, index) => [
      company,
      options.empty ? [] : Array.from({ length: 21 }, (_, n) => lifecycleCaseSeed(index, n + 1)),
    ]),
  );
  const events = new Map<string, LifecycleHistoryPageDto["items"]>(
    [...records.values()].flatMap((cases) =>
      cases.map((record) => [
        record.id,
        Array.from(
          { length: record.version + 1 },
          (_, version): LifecycleHistoryPageDto["items"][number] => ({
            version,
            taskKey: version === 0 ? null : "equipment",
            action: version === 0 ? "CREATED" : version % 2 === 0 ? "TASK_PENDING" : "TASK_DONE",
            assigneeId: lifecycleAccountId,
            actorId: lifecycleAccountId,
            reason: version === 0 ? "Employee transition" : "Equipment verification",
            recordedAt: "2026-10-01T01:00:00Z",
          }),
        ),
      ]),
    ),
  );
  const reads: URL[] = [];
  const completedReads: URL[] = [];
  // Strict Mode probes cancellation on mount. Count completed requests separately from attempts.
  page.on("requestfinished", (request) => {
    const url = new URL(request.url());
    if (
      request.method() === "GET" &&
      url.pathname.startsWith("/api/v1/companies/") &&
      url.pathname.includes("/lifecycle/")
    )
      completedReads.push(url);
  });
  let readFailure: string | null = null;
  let readGate: ReturnType<typeof gate> | null = null;
  await page.route(
    "**/api/v1/companies/*/lifecycle/{cases,cases?*,cases/**,tasks/assigned,tasks/assigned?*}",
    async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      const company = url.pathname.split("/")[4] ?? "";
      const id = url.pathname.split("/")[7];
      expect(records.has(company)).toBe(true);
      expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
      expect(request.method()).toBe("GET");
      reads.push(url);
      if (readFailure)
        return route.fulfill({
          status: readFailure === "mfa_required" || readFailure === "access_denied" ? 403 : 503,
          json: {
            code: readFailure,
            fields: {},
            parameters: {},
            detail: "PRIVATE LIFECYCLE DIAGNOSTIC",
          },
        });
      const source = records.get(company) ?? [];
      let payload: unknown;
      if (url.pathname.endsWith("/tasks/assigned")) {
        expect(url.searchParams.get("limit")).toBe("50");
        const after = url.searchParams.get("after");
        const items = source
          .filter((item) => item.status === "OPEN")
          .slice(0, 17)
          .flatMap((item) =>
            item.tasks
              .filter((task) => task.status === "PENDING" && task.assigneeId === lifecycleAccountId)
              .map((task) => ({
                caseId: item.id,
                employmentId: item.employmentId,
                employee: item.employee,
                caseVersion: item.version,
                kind: item.kind,
                task,
              })),
          );
        const filtered = items.filter(
          (item) => after === null || `${item.caseId}:${item.task.key}` > after,
        );
        const last = filtered[49];
        payload = {
          items: filtered.slice(0, 50),
          nextCursor: filtered.length > 50 && last ? `${last.caseId}:${last.task.key}` : null,
        };
      } else if (id) {
        const record = source.find((item) => item.id === id);
        if (!record)
          return route.fulfill({
            status: 404,
            json: { code: "lifecycle_case_not_found", fields: {}, parameters: {} },
          });
        if (url.pathname.endsWith("/history")) {
          expect(url.searchParams.get("limit")).toBe("50");
          const after = Number(url.searchParams.get("after") ?? -1);
          const items = (events.get(record.id) ?? []).filter((event) => event.version > after);
          payload = {
            items: items.slice(0, 50),
            nextCursor: items.length > 50 ? String(items[49]?.version) : null,
          };
        } else payload = record;
      } else {
        expect(url.searchParams.get("limit")).toBe("10");
        const after = url.searchParams.get("after");
        const employment = url.searchParams.get("employmentId");
        const status = url.searchParams.get("status");
        const filtered = source.filter(
          (item) =>
            (after === null || item.id > after) &&
            (employment === null || item.employmentId === employment) &&
            (status === null || item.status === status),
        );
        payload = {
          items: filtered.slice(0, 10),
          nextCursor: filtered.length > 10 ? filtered[9]?.id : null,
        };
      }
      const snapshot = structuredClone(payload);
      const pending = readGate;
      readGate = null;
      if (pending) {
        pending.enter();
        await pending.held;
      }
      return route.fulfill({ json: snapshot });
    },
  );
  return {
    identity,
    reads,
    completedReads,
    records,
    events,
    failReads(code: string | null) {
      readFailure = code;
    },
    holdNextRead() {
      const pending = gate();
      readGate = pending;
      return pending;
    },
  };
}
