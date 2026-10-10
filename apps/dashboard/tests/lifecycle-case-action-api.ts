import { expect, type Page } from "@playwright/test";
import type { LifecycleCaseChangeDto } from "../src/features/lifecycle/data/models/lifecycle-case-change-dto";
import { companyIds } from "./identity-api";
import { lifecycleAccountId, lifecycleCaseId } from "./lifecycle-cases-api";
import { installLifecycleTaskApi } from "./lifecycle-task-api";

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
export async function installLifecycleCaseActionApi(
  page: Page,
  options: { permissions?: string[] } = {},
) {
  const api = await installLifecycleTaskApi(page, {
    permissions: options.permissions ?? ["people.lifecycle.read", "people.lifecycle.manage"],
  });
  const caseChanges: {
    company: string;
    id: string;
    action: string;
    operation: string;
    csrf: string | undefined;
    body: LifecycleCaseChangeDto;
  }[] = [];
  const receipts = new Map<
    string,
    { id: string; action: string; body: string; receipt: { id: string; version: number } }
  >();
  let commits = 0;
  let lose = false;
  let failure: { code: string; status: number } | null = null;
  let pendingWrite: ReturnType<typeof gate> | null = null;
  await page.route(
    "**/api/v1/companies/*/lifecycle/cases/*/{cancel,complete-onboarding}",
    async (route) => {
      const request = route.request();
      expect(request.method()).toBe("POST");
      const parts = new URL(request.url()).pathname.split("/");
      const company = parts[4] ?? "";
      const id = parts[7] ?? "";
      const action = parts[8] ?? "";
      const operation = request.headers()["idempotency-key"] ?? "";
      const csrf = request.headers()["x-csrf-token"];
      const body = request.postDataJSON() as LifecycleCaseChangeDto;
      expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
      expect(csrf).toBeTruthy();
      expect(Object.keys(body).sort()).toEqual(["expectedVersion", "reason"]);
      caseChanges.push({ company, id, action, operation, csrf, body });
      const waiting = pendingWrite;
      pendingWrite = null;
      if (waiting) {
        waiting.enter();
        await waiting.held;
      }
      const reject = (code: string, status = 409) =>
        route.fulfill({
          status,
          json: { code, fields: {}, parameters: {}, detail: "PRIVATE CASE" },
        });
      if (failure) {
        const rejected = failure;
        failure = null;
        return reject(rejected.code, rejected.status);
      }
      const key = `${company}:${operation}`;
      const replay = receipts.get(key);
      if (replay) {
        expect(replay.body).toBe(JSON.stringify(body));
        expect(replay.id).toBe(id);
        expect(replay.action).toBe(action);
        return route.fulfill({ json: replay.receipt });
      }
      const record = api.records.get(company)?.find((item) => item.id === id);
      if (!record) return reject("lifecycle_case_not_found", 404);
      if (record.version !== body.expectedVersion) return reject("stale_version");
      if (
        record.status !== "OPEN" ||
        (action === "complete-onboarding" && record.kind !== "ONBOARDING")
      )
        return reject("lifecycle_case_not_open");
      if (action === "complete-onboarding") {
        if (record.tasks.some((task) => task.required && task.status !== "DONE"))
          return reject("required_lifecycle_tasks_pending");
        if (record.tasks.some((task) => task.status === "PENDING"))
          return reject("lifecycle_tasks_unresolved");
      }
      record.status = action === "cancel" ? "CANCELLED" : "COMPLETED";
      record.version++;
      api.events.get(id)?.push({
        version: record.version,
        taskKey: null,
        action: record.status,
        actorId: lifecycleAccountId,
        assigneeId: null,
        reason: body.reason,
        recordedAt: "2026-10-01T03:00:00Z",
      });
      const receipt = { id, version: record.version };
      receipts.set(key, { id, action, body: JSON.stringify(body), receipt });
      commits++;
      if (lose) {
        lose = false;
        return route.abort("connectionfailed");
      }
      return route.fulfill({ json: receipt });
    },
  );
  return {
    ...api,
    caseChanges,
    get caseCommits() {
      return commits;
    },
    loseCaseChange: () => {
      lose = true;
    },
    rejectCaseChange: (code: string, status = 409) => {
      failure = { code, status };
    },
    holdCaseChange: () => {
      const held = gate();
      pendingWrite = held;
      return held;
    },
    resolveTasks: () => {
      const record = api.records
        .get(companyIds[0])
        ?.find((item) => item.id === lifecycleCaseId(0, 3));
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
          reason: "Prepared checklist",
          recordedAt: "2026-10-01T02:00:00Z",
        });
      }
    },
    reopenEquipment: () => {
      const record = api.records
        .get(companyIds[0])
        ?.find((item) => item.id === lifecycleCaseId(0, 3));
      const task = record?.tasks.find((item) => item.key === "equipment");
      if (!record || !task) throw new Error("Expected equipment task");
      task.status = "PENDING";
      task.completedBy = null;
      task.completedAt = null;
      record.version++;
      api.events.get(record.id)?.push({
        version: record.version,
        taskKey: task.key,
        action: "TASK_PENDING",
        actorId: lifecycleAccountId,
        assigneeId: task.assigneeId,
        reason: "Equipment requires review",
        recordedAt: "2026-10-01T02:30:00Z",
      });
    },
  };
}
