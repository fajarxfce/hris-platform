import { expect, type Page } from "@playwright/test";
import type { LifecycleTaskAssignmentDto } from "../src/features/lifecycle/data/models/lifecycle-task-assignment-dto";
import { companyIds } from "./identity-api";
import { lifecycleAccountId } from "./lifecycle-cases-api";
import { installLifecycleTaskApi } from "./lifecycle-task-api";

export const lifecycleMemberId = (company = 0, n = 1) =>
  `d0000000-abcd-4000-8000-${company + 1}${String(n).padStart(11, "0")}`;
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
export async function installLifecycleAssignmentApi(
  page: Page,
  options: { permissions?: string[] } = {},
) {
  const api = await installLifecycleTaskApi(page, {
    permissions: options.permissions ?? ["people.lifecycle.read", "people.lifecycle.manage"],
  });
  const members = new Map<string, { id: string; displayName: string }[]>(
    companyIds.map((company, index) => [
      company,
      [
        { id: lifecycleAccountId, displayName: "Current member" },
        ...Array.from({ length: 51 }, (_, n) => ({
          id: lifecycleMemberId(index, n + 1),
          displayName: `${index === 0 ? "North" : "South"} member ${n + 1}`,
        })),
      ],
    ]),
  );
  const assigneeReads: URL[] = [];
  const assignments: {
    company: string;
    id: string;
    taskKey: string;
    operation: string;
    csrf: string | undefined;
    body: LifecycleTaskAssignmentDto;
  }[] = [];
  const receipts = new Map<
    string,
    { id: string; taskKey: string; body: string; receipt: { id: string; version: number } }
  >();
  let commits = 0;
  let lose = false;
  let failure: { code: string; status: number } | null = null;
  let lookupGate: ReturnType<typeof gate> | null = null;
  let writeGate: ReturnType<typeof gate> | null = null;
  await page.route("**/api/v1/companies/*/lifecycle/assignees{,?*}", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    expect(request.method()).toBe("GET");
    expect(url.searchParams.get("limit")).toBe("50");
    assigneeReads.push(url);
    const company = url.pathname.split("/")[4] ?? "";
    const query = (url.searchParams.get("query") ?? "").toLowerCase();
    const after = url.searchParams.get("after");
    const filtered = (members.get(company) ?? []).filter(
      (member) =>
        (after === null || member.id > after) && member.displayName.toLowerCase().includes(query),
    );
    const snapshot = structuredClone({
      items: filtered.slice(0, 50),
      nextCursor: filtered.length > 50 ? filtered[49]?.id : null,
    });
    const held = lookupGate;
    lookupGate = null;
    if (held) {
      held.enter();
      await held.held;
    }
    return route.fulfill({ json: snapshot });
  });
  await page.route("**/api/v1/companies/*/lifecycle/cases/*/tasks/*/assignee", async (route) => {
    const request = route.request();
    const parts = new URL(request.url()).pathname.split("/");
    expect(request.method()).toBe("PUT");
    const company = parts[4] ?? "";
    const id = parts[7] ?? "";
    const taskKey = parts[9] ?? "";
    const body = request.postDataJSON() as LifecycleTaskAssignmentDto;
    const operation = request.headers()["idempotency-key"] ?? "";
    const csrf = request.headers()["x-csrf-token"];
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    expect(csrf).toBeTruthy();
    expect(Object.keys(body).sort()).toEqual(["assigneeId", "expectedVersion", "reason"]);
    assignments.push({ company, id, taskKey, operation, csrf, body });
    const held = writeGate;
    writeGate = null;
    if (held) {
      held.enter();
      await held.held;
    }
    const reject = (code: string, status = 409) =>
      route.fulfill({
        status,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE ASSIGNMENT" },
      });
    if (failure) {
      const rejected = failure;
      failure = null;
      return reject(rejected.code, rejected.status);
    }
    const receiptKey = `${company}:${operation}`;
    const replay = receipts.get(receiptKey);
    if (replay) {
      expect(replay.body).toBe(JSON.stringify(body));
      expect(replay.id).toBe(id);
      expect(replay.taskKey).toBe(taskKey);
      return route.fulfill({ json: replay.receipt });
    }
    const record = api.records.get(company)?.find((item) => item.id === id);
    const task = record?.tasks.find((item) => item.key === taskKey);
    if (!record || !task) return reject("lifecycle_task_not_found", 404);
    if (record.version !== body.expectedVersion) return reject("stale_version");
    if (record.status !== "OPEN") return reject("lifecycle_case_not_open");
    if (task.status !== "PENDING" || task.assigneeId === body.assigneeId)
      return reject("lifecycle_task_not_assignable");
    if (
      body.assigneeId !== null &&
      !members.get(company)?.some((item) => item.id === body.assigneeId)
    )
      return reject("lifecycle_assignee_unavailable", 422);
    task.assigneeId = body.assigneeId;
    record.version++;
    api.events.get(record.id)?.push({
      version: record.version,
      taskKey,
      action: "ASSIGNED",
      actorId: lifecycleAccountId,
      assigneeId: body.assigneeId,
      reason: body.reason,
      recordedAt: "2026-10-01T02:00:00Z",
    });
    const receipt = { id, version: record.version };
    receipts.set(receiptKey, { id, taskKey, body: JSON.stringify(body), receipt });
    commits++;
    if (lose) {
      lose = false;
      return route.abort("connectionfailed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    ...api,
    members,
    assigneeReads,
    assignments,
    get assignmentCommits() {
      return commits;
    },
    loseAssignment() {
      lose = true;
    },
    rejectAssignment(code: string, status = 409) {
      failure = { code, status };
    },
    removeMember(company: string, id: string) {
      members.set(
        company,
        (members.get(company) ?? []).filter((member) => member.id !== id),
      );
    },
    holdNextLookup() {
      const held = gate();
      lookupGate = held;
      return held;
    },
    holdNextAssignment() {
      const held = gate();
      writeGate = held;
      return held;
    },
  };
}
