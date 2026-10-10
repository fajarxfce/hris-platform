import { expect, type Page } from "@playwright/test";
import type { OffboardingCompletionDto } from "../src/features/lifecycle/data/models/offboarding-dto";
import { companyIds } from "./identity-api";
import { lifecycleAccountId, lifecycleCaseId } from "./lifecycle-cases-api";
import { installLifecycleTaskApi } from "./lifecycle-task-api";

export const offboardingPermissions = [
  "people.lifecycle.read",
  "people.lifecycle.manage",
  "people.manage",
  "people.offboard",
];
function gate() {
  let enter!: () => void;
  let release!: () => void;
  const entered = new Promise<void>((done) => {
    enter = done;
  });
  const held = new Promise<void>((done) => {
    release = done;
  });
  return { entered, held, enter, release };
}
export async function installOffboardingApi(page: Page, permissions = offboardingPermissions) {
  const api = await installLifecycleTaskApi(page, { permissions });
  const employments = new Map<string, { version: number; ended: boolean }>(
    [...api.records.values()].flatMap((records) =>
      records.map((record) => [record.employmentId, { version: 7, ended: false }] as const),
    ),
  );
  const reviews: URL[] = [];
  const completions: {
    company: string;
    id: string;
    operation: string;
    csrf: string | undefined;
    body: OffboardingCompletionDto;
  }[] = [];
  const receipts = new Map<
    string,
    { body: string; id: string; receipt: { id: string; version: number } }
  >();
  let companyDate = "2026-10-02";
  let reviewFailure: string | null = null;
  let failure: { code: string; status: number } | null = null;
  let readGate: ReturnType<typeof gate> | null = null;
  let writeGate: ReturnType<typeof gate> | null = null;
  let lose = false;
  let completionsCommitted = 0;
  await page.route("**/api/v1/companies/*/lifecycle/cases/*/offboarding-review", async (route) => {
    const request = route.request();
    expect(request.method()).toBe("GET");
    const url = new URL(request.url());
    const company = url.pathname.split("/")[4] ?? "";
    const id = url.pathname.split("/")[7] ?? "";
    reviews.push(url);
    const reject = (code: string, status = 409) =>
      route.fulfill({
        status,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE OFFBOARDING" },
      });
    if (reviewFailure) return reject(reviewFailure, 403);
    if (!offboardingPermissions.every((permission) => permissions.includes(permission)))
      return reject("offboarding_access_required", 403);
    const record = api.records.get(company)?.find((item) => item.id === id);
    if (!record) return reject("lifecycle_case_not_found", 404);
    if (record.kind !== "OFFBOARDING" || record.status !== "OPEN")
      return reject("lifecycle_case_not_open");
    const employment = employments.get(record.employmentId);
    if (!employment) return reject("employee_not_found", 404);
    const payload = structuredClone({
      case: record,
      employmentVersion: employment.version,
      today: companyDate,
    });
    const pending = readGate;
    readGate = null;
    if (pending) {
      pending.enter();
      await pending.held;
    }
    return route.fulfill({ json: payload });
  });
  await page.route(
    "**/api/v1/companies/*/lifecycle/cases/*/complete-offboarding",
    async (route) => {
      const request = route.request();
      expect(request.method()).toBe("POST");
      const parts = new URL(request.url()).pathname.split("/");
      const company = parts[4] ?? "";
      const id = parts[7] ?? "";
      const operation = request.headers()["idempotency-key"] ?? "";
      const csrf = request.headers()["x-csrf-token"];
      const body = request.postDataJSON() as OffboardingCompletionDto;
      expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
      expect(csrf).toBeTruthy();
      expect(Object.keys(body).sort()).toEqual(["employmentVersion", "expectedVersion", "reason"]);
      completions.push({ company, id, operation, csrf, body });
      const pending = writeGate;
      writeGate = null;
      if (pending) {
        pending.enter();
        await pending.held;
      }
      const reject = (code: string, status = 409) =>
        route.fulfill({
          status,
          json: { code, fields: {}, parameters: {}, detail: "PRIVATE OFFBOARDING" },
        });
      if (failure) {
        const denied = failure;
        failure = null;
        return reject(denied.code, denied.status);
      }
      const key = `${company}:${operation}`;
      const replay = receipts.get(key);
      if (replay) {
        expect(replay.id).toBe(id);
        expect(replay.body).toBe(JSON.stringify(body));
        return route.fulfill({ json: replay.receipt });
      }
      const record = api.records.get(company)?.find((item) => item.id === id);
      if (!record) return reject("lifecycle_case_not_found", 404);
      const employment = employments.get(record.employmentId);
      if (!employment) return reject("employee_not_found", 404);
      if (record.version !== body.expectedVersion) return reject("stale_version");
      if (record.status !== "OPEN" || record.kind !== "OFFBOARDING")
        return reject("lifecycle_case_not_open");
      if (record.tasks.some((task) => task.required && task.status !== "DONE"))
        return reject("required_lifecycle_tasks_pending");
      if (record.tasks.some((task) => task.status === "PENDING"))
        return reject("lifecycle_tasks_unresolved");
      if (record.targetDate >= companyDate) return reject("offboarding_date_not_reached");
      if (employment.version !== body.employmentVersion) return reject("stale_employment_version");
      record.status = "COMPLETED";
      record.version++;
      employment.version++;
      employment.ended = true;
      api.events.get(id)?.push({
        version: record.version,
        taskKey: null,
        action: "COMPLETED",
        actorId: lifecycleAccountId,
        assigneeId: null,
        reason: body.reason,
        recordedAt: "2026-10-02T00:00:00Z",
      });
      const receipt = { id, version: record.version };
      receipts.set(key, { id, body: JSON.stringify(body), receipt });
      completionsCommitted++;
      if (lose) {
        lose = false;
        return route.abort("connectionfailed");
      }
      return route.fulfill({ json: receipt });
    },
  );
  return {
    ...api,
    employments,
    reviews,
    completions,
    get completionsCommitted() {
      return completionsCommitted;
    },
    setCompanyDate: (date: string) => {
      companyDate = date;
    },
    rejectReview: (code: string | null) => {
      reviewFailure = code;
    },
    rejectCompletion: (code: string, status = 409) => {
      failure = { code, status };
    },
    loseCompletion: () => {
      lose = true;
    },
    holdReview: () => {
      const pending = gate();
      readGate = pending;
      return pending;
    },
    holdCompletion: () => {
      const pending = gate();
      writeGate = pending;
      return pending;
    },
    advanceEmployment: () => {
      const record = api.records
        .get(companyIds[0])
        ?.find((item) => item.id === lifecycleCaseId(0, 2));
      const employment = record && employments.get(record.employmentId);
      if (!employment) throw new Error("Expected employment");
      employment.version++;
    },
    resolveTasks: () => {
      const record = api.records
        .get(companyIds[0])
        ?.find((item) => item.id === lifecycleCaseId(0, 2));
      if (!record) throw new Error("Expected case");
      for (const task of record.tasks.filter((task) => task.status === "PENDING")) {
        task.status = task.required ? "DONE" : "WAIVED";
        task.completedBy = lifecycleAccountId;
        task.completedAt = "2026-10-01T02:00:00Z";
        record.version++;
        api.events.get(record.id)?.push({
          version: record.version,
          taskKey: task.key,
          action: task.required ? "TASK_DONE" : "TASK_WAIVED",
          actorId: lifecycleAccountId,
          assigneeId: task.assigneeId,
          reason: "Checklist reviewed",
          recordedAt: "2026-10-01T02:00:00Z",
        });
      }
    },
    reopenTask: (key: string) => {
      const record = api.records
        .get(companyIds[0])
        ?.find((item) => item.id === lifecycleCaseId(0, 2));
      const task = record?.tasks.find((item) => item.key === key);
      if (!record || !task) throw new Error("Expected task");
      task.status = "PENDING";
      task.completedBy = null;
      task.completedAt = null;
      record.version++;
      api.events.get(record.id)?.push({
        version: record.version,
        taskKey: key,
        action: "TASK_PENDING",
        actorId: lifecycleAccountId,
        assigneeId: task.assigneeId,
        reason: "Checklist reopened",
        recordedAt: "2026-10-01T02:30:00Z",
      });
    },
  };
}
