import { expect, type Page } from "@playwright/test";
import type { LifecycleTaskChangeDto } from "../src/features/lifecycle/data/models/lifecycle-task-change-dto";
import { companyIds } from "./identity-api";
import {
  installLifecycleCasesApi,
  lifecycleAccountId,
  lifecycleCaseId,
} from "./lifecycle-cases-api";

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
export async function installLifecycleTaskApi(
  page: Page,
  options: { permissions?: string[] } = {},
) {
  const api = await installLifecycleCasesApi(page, options);
  const permissions = options.permissions ?? ["people.lifecycle.read", "people.lifecycle.perform"];
  const writes: {
    company: string;
    id: string;
    taskKey: string;
    operation: string;
    csrf: string | undefined;
    body: LifecycleTaskChangeDto;
  }[] = [];
  const receipts = new Map<
    string,
    { body: string; id: string; taskKey: string; receipt: { id: string; version: number } }
  >();
  let commits = 0;
  let lose = false;
  let nextFailure: { code: string; status: number } | null = null;
  let writeGate: ReturnType<typeof gate> | null = null;
  await page.route("**/api/v1/companies/*/lifecycle/cases/*/tasks/*", async (route) => {
    const request = route.request();
    if (request.method() !== "PUT") return route.fallback();
    const parts = new URL(request.url()).pathname.split("/");
    const company = parts[4] ?? "";
    const id = parts[7] ?? "";
    const taskKey = parts[9] ?? "";
    const body = request.postDataJSON() as LifecycleTaskChangeDto;
    const operation = request.headers()["idempotency-key"] ?? "";
    const csrf = request.headers()["x-csrf-token"];
    expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    expect(csrf).toBeTruthy();
    expect(Object.keys(body).sort()).toEqual(["expectedVersion", "reason", "status"]);
    writes.push({ company, id, taskKey, operation, csrf, body });
    const pending = writeGate;
    writeGate = null;
    if (pending) {
      pending.enter();
      await pending.held;
    }
    const failure = (code: string, status = 409) =>
      route.fulfill({
        status,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE TASK DETAILS" },
      });
    if (nextFailure) {
      const rejected = nextFailure;
      nextFailure = null;
      return failure(rejected.code, rejected.status);
    }
    const record = api.records.get(company)?.find((item) => item.id === id);
    const task = record?.tasks.find((item) => item.key === taskKey);
    if (!record || !task) return failure("lifecycle_task_not_found", 404);
    const manager = permissions.includes("people.lifecycle.manage");
    if (
      !manager &&
      (!permissions.includes("people.lifecycle.perform") || task.assigneeId !== lifecycleAccountId)
    )
      return failure("lifecycle_task_not_assigned", 403);
    const receiptKey = `${company}:${operation}`;
    const replay = receipts.get(receiptKey);
    if (replay) {
      expect(JSON.stringify(body)).toBe(replay.body);
      expect(id).toBe(replay.id);
      expect(taskKey).toBe(replay.taskKey);
      return route.fulfill({ json: replay.receipt });
    }
    if (record.version !== body.expectedVersion) return failure("stale_version");
    if (record.status !== "OPEN") return failure("lifecycle_case_not_open");
    if (body.status === "WAIVED" && (!manager || task.required))
      return failure("lifecycle_task_cannot_be_waived", 422);
    if (body.status === task.status) return failure("lifecycle_task_unchanged");
    task.status = body.status;
    task.completedBy = body.status === "PENDING" ? null : lifecycleAccountId;
    task.completedAt = body.status === "PENDING" ? null : "2026-10-01T02:00:00Z";
    record.version++;
    api.events.get(record.id)?.push({
      version: record.version,
      taskKey,
      action:
        body.status === "PENDING"
          ? "TASK_PENDING"
          : body.status === "DONE"
            ? "TASK_DONE"
            : "TASK_WAIVED",
      actorId: lifecycleAccountId,
      assigneeId: task.assigneeId,
      reason: body.reason,
      recordedAt: "2026-10-01T02:00:00Z",
    });
    const receipt = { id, version: record.version };
    receipts.set(receiptKey, { body: JSON.stringify(body), id, taskKey, receipt });
    commits++;
    if (lose) {
      lose = false;
      return route.abort("connectionfailed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    ...api,
    writes,
    get commits() {
      return commits;
    },
    loseNext() {
      lose = true;
    },
    rejectNext(code: string, status = 409) {
      nextFailure = { code, status };
    },
    holdNextWrite() {
      const pending = gate();
      writeGate = pending;
      return pending;
    },
    advance() {
      const record = api.records.get(companyIds[0])?.find((item) => item.id === lifecycleCaseId());
      if (!record) throw new Error("Expected case");
      record.version++;
      api.events.get(record.id)?.push({
        version: record.version,
        taskKey: "equipment",
        action: "TASK_PENDING",
        actorId: lifecycleAccountId,
        assigneeId: lifecycleAccountId,
        reason: "Concurrent review",
        recordedAt: "2026-10-01T02:00:00Z",
      });
    },
  };
}
