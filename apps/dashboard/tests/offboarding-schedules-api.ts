import { expect, type Page } from "@playwright/test";
import type { OffboardingScheduleDto } from "../src/features/lifecycle/data/models/offboarding-schedule-dto";
import { companyIds } from "./identity-api";
import { lifecycleAccountId, lifecycleCaseId } from "./lifecycle-cases-api";
import { installOffboardingApi, offboardingPermissions } from "./offboarding-api";
export const scheduledCaseId = lifecycleCaseId(0, 2);
export const scheduledId = "b0000000-abcd-4000-8000-000000000099";
export const scheduledJobId = "c0000000-abcd-4000-8000-000000000099";
function gate() {
  let enter!: () => void, release!: () => void;
  const entered = new Promise<void>((resolve) => {
    enter = resolve;
  });
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  return { enter, release, entered, held };
}
export async function installOffboardingSchedulesApi(
  page: Page,
  permissions = offboardingPermissions,
) {
  const api = await installOffboardingApi(page, permissions);
  api.resolveTasks();
  const record = api.records.get(companyIds[0])?.find((item) => item.id === scheduledCaseId);
  if (!record) throw new Error("Expected case");
  const items: OffboardingScheduleDto[] = [],
    reads: URL[] = [];
  const writes: {
    operation: string;
    company: string;
    action: string;
    body: Record<string, unknown>;
  }[] = [];
  const receipts = new Map<
    string,
    { body: string; action: string; receipt: { id: string; version: number } }
  >();
  let basis = "a".repeat(64),
    employmentVersion = 7,
    blockers: string[] = [],
    readFailure: string | null = null;
  let commandFailure: { code: string; status: number } | null = null,
    lose = false,
    committed = 0;
  let readGate: ReturnType<typeof gate> | null = null,
    writeGate: ReturnType<typeof gate> | null = null;
  const seed = (): OffboardingScheduleDto => ({
    id: scheduledId,
    caseId: scheduledCaseId,
    employmentId: record.employmentId,
    status: "SCHEDULED",
    version: 0,
    caseVersion: record.version,
    employmentVersion,
    appliedEmploymentRevision: employmentVersion,
    lastWorkingDate: "2026-10-01",
    companyTimezone: "Asia/Jakarta",
    scheduledFor: "2026-10-01T17:00:00Z",
    validUntil: "2026-10-08T17:00:00Z",
    attempt: 1,
    createdBy: lifecycleAccountId,
    createdAt: "2026-10-01T03:00:00Z",
    reason: "Departure reviewed",
    finishedAt: null,
    failureCode: null,
    job: {
      id: scheduledJobId,
      status: "QUEUED",
      version: 0,
      attempts: 0,
      cancellationRequested: false,
      completedItems: 0,
      failureCode: null,
      finishedAt: null,
    },
    actions: ["cancel"],
  });
  await page.route(
    /\/api\/v1\/companies\/[^/]+\/lifecycle\/cases\/[^/]+\/offboarding-schedules(?:\/.*)?$/u,
    async (route) => {
      const request = route.request(),
        url = new URL(request.url()),
        parts = url.pathname.split("/"),
        company = parts[4] ?? "",
        caseId = parts[7],
        id = parts[9],
        action = parts[10] ?? "schedule";
      const reject = (code: string, status = 409) =>
        route.fulfill({
          status,
          json: { code, fields: {}, parameters: {}, detail: "PRIVATE SCHEDULE DIAGNOSTIC" },
        });
      if (!offboardingPermissions.every((p) => permissions.includes(p)))
        return reject("offboarding_access_required", 403);
      if (company !== companyIds[0] || caseId !== scheduledCaseId)
        return reject("lifecycle_case_not_found", 404);
      if (request.method() === "GET") {
        reads.push(url);
        if (readFailure) return reject(readFailure, 403);
        const payload =
          id === "review"
            ? {
                case: record,
                employmentVersion,
                appliedEmploymentRevision: employmentVersion,
                companyTimezone: "Asia/Jakarta",
                today: "2026-10-01",
                scheduledFor: "2026-10-01T17:00:00Z",
                validUntil: "2026-10-08T17:00:00Z",
                basisRevision: basis,
                attempts: items.length,
                blockers,
              }
            : id
              ? items.find((item) => item.id === id)
              : { items, nextCursor: null };
        if (!payload) return reject("offboarding_schedule_not_found", 404);
        const snapshot = structuredClone(payload),
          pending = readGate;
        readGate = null;
        if (pending) {
          pending.enter();
          await pending.held;
        }
        return route.fulfill({ json: snapshot });
      }
      expect(request.method()).toBe("POST");
      const body = request.postDataJSON() as Record<string, unknown>,
        operation = request.headers()["idempotency-key"] ?? "";
      expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
      expect(request.headers()["x-csrf-token"]).toBeTruthy();
      expect(Object.keys(body).sort()).toEqual(
        action === "schedule"
          ? ["basisRevision", "employmentVersion", "expectedVersion", "id", "reason"]
          : ["expectedJobVersion", "expectedVersion", "reason"],
      );
      writes.push({ operation, company, action, body });
      const pending = writeGate;
      writeGate = null;
      if (pending) {
        pending.enter();
        await pending.held;
      }
      if (commandFailure) {
        const failure = commandFailure;
        commandFailure = null;
        return reject(failure.code, failure.status);
      }
      const replay = receipts.get(operation);
      if (replay) {
        expect(replay.body).toBe(JSON.stringify(body));
        expect(replay.action).toBe(action);
        return route.fulfill({ json: replay.receipt });
      }
      let receipt: { id: string; version: number };
      if (action === "schedule") {
        if (
          body.basisRevision !== basis ||
          body.expectedVersion !== record.version ||
          body.employmentVersion !== employmentVersion
        )
          return reject("offboarding_schedule_changed");
        if (items.some((item) => item.status === "SCHEDULED"))
          return reject("offboarding_schedule_active");
        if (blockers[0]) return reject(blockers[0]);
        const schedule = {
          ...seed(),
          id: String(body.id),
          reason: String(body.reason),
          attempt: items.length + 1,
        };
        items.push(schedule);
        receipt = { id: schedule.id, version: 0 };
      } else {
        const schedule = items.find((item) => item.id === id);
        if (!schedule) return reject("offboarding_schedule_not_found", 404);
        if (
          schedule.version !== body.expectedVersion ||
          schedule.job.version !== body.expectedJobVersion
        )
          return reject("stale_version");
        if (!schedule.actions.includes(action === "cancel" ? "cancel" : "recover"))
          return reject("offboarding_schedule_finished");
        if (action === "cancel") {
          schedule.job.cancellationRequested = true;
          schedule.job.version++;
          schedule.actions = [];
          receipt = { id: schedule.job.id, version: schedule.job.version };
        } else {
          schedule.status = schedule.job.status === "CANCELLED" ? "CANCELLED" : "FAILED";
          schedule.version = 1;
          schedule.finishedAt = "2026-10-02T03:00:00Z";
          schedule.failureCode = schedule.job.failureCode;
          schedule.actions = [];
          receipt = { id: schedule.id, version: 1 };
        }
      }
      committed++;
      receipts.set(operation, { body: JSON.stringify(body), action, receipt });
      if (lose) {
        lose = false;
        return route.abort("connectionfailed");
      }
      return route.fulfill({ json: receipt });
    },
  );
  return {
    ...api,
    items,
    scheduleReads: reads,
    writes,
    record,
    seed,
    get committed() {
      return committed;
    },
    setBlockers: (values: string[]) => {
      blockers = values;
    },
    changeBasis: () => {
      basis = "b".repeat(64);
      employmentVersion++;
      record.version++;
    },
    rejectReads: (value: string | null) => {
      readFailure = value;
    },
    rejectCommand: (code: string, status = 409) => {
      commandFailure = { code, status };
    },
    loseCommand: () => {
      lose = true;
    },
    holdScheduleRead: () => {
      const held = gate();
      readGate = held;
      return held;
    },
    holdScheduleCommand: () => {
      const held = gate();
      writeGate = held;
      return held;
    },
    stopJob: () => {
      const item = items[0];
      if (!item) throw new Error("Expected schedule");
      item.job = {
        ...item.job,
        status: "FAILED",
        attempts: 4,
        version: 4,
        finishedAt: "2026-10-02T02:00:00Z",
        failureCode: "job_attempts_exhausted",
      };
      item.actions = ["recover"];
    },
    finishCancellation: () => {
      const item = items[0];
      if (!item) throw new Error("Expected schedule");
      item.status = "CANCELLED";
      item.version = 1;
      item.finishedAt = "2026-10-01T03:10:00Z";
      item.failureCode = "job_cancelled";
      item.job = {
        ...item.job,
        status: "CANCELLED",
        version: item.job.version + 1,
        finishedAt: item.finishedAt,
        failureCode: "job_cancelled",
      };
      item.actions = [];
    },
  };
}
