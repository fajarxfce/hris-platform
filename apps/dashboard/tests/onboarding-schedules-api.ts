import { expect, type Page } from "@playwright/test";
import type {
  OnboardingScheduleDto,
  OnboardingScheduleReviewDto,
} from "../src/features/lifecycle/data/models/onboarding-schedule-dto";
import type { EmployeeDto } from "../src/features/people/data/models/employee-dto";
import { companyIds } from "./identity-api";
import {
  installLifecycleCaseCreationApi,
  lifecycleEmployeeId,
} from "./lifecycle-case-creation-api";
import { lifecycleAccountId } from "./lifecycle-cases-api";
export const onboardingPermissions = [
  "people.read",
  "people.lifecycle.read",
  "people.lifecycle.manage",
];
export const scheduledEmployee = lifecycleEmployeeId();
export const scheduledId = "b0000000-abcd-4000-8000-000000000098";
export const scheduledJobId = "c0000000-abcd-4000-8000-000000000098";
export const scheduledCaseId = "d0000000-abcd-4000-8000-000000000098";
export const assigneeId = "a0000000-abcd-4000-8000-000000000078";
function gate() {
  let enter!: () => void, release!: () => void;
  const entered = new Promise<void>((done) => {
    enter = done;
  });
  const held = new Promise<void>((done) => {
    release = done;
  });
  return { enter, release, entered, held };
}
export async function installOnboardingSchedulesApi(
  page: Page,
  permissions = onboardingPermissions,
) {
  const api = await installLifecycleCaseCreationApi(page, { permissions });
  const employee: EmployeeDto = {
    id: scheduledEmployee,
    companyId: companyIds[0],
    employeeNumber: "JOIN-001",
    person: { legalName: "North joining employee", email: null },
    terms: {
      effectiveFrom: "2026-10-04",
      startDate: "2026-10-04",
      endDate: null,
      contract: "PERMANENT",
      status: "ACTIVE",
    },
    version: 7,
    appliedRevision: 6,
  };
  const template = api.templates.get(companyIds[0])?.[0];
  if (!template) throw new Error("Expected template");
  const items: OnboardingScheduleDto[] = [],
    reads: URL[] = [],
    employeeReads: URL[] = [],
    assigneeReads: URL[] = [];
  const writes: {
    company: string;
    operation: string;
    action: string;
    body: Record<string, unknown>;
  }[] = [];
  const receipts = new Map<
    string,
    { body: string; action: string; receipt: { id: string; version: number } }
  >();
  let basis = "a".repeat(64),
    blockers: string[] = [],
    readFailure: string | null = null,
    commandFailure: { code: string; status: number } | null = null,
    lose = false,
    committed = 0;
  let readGate: ReturnType<typeof gate> | null = null,
    writeGate: ReturnType<typeof gate> | null = null;
  const midnight = (date: string) =>
    new Date(Date.parse(`${date}T00:00:00Z`) - 7 * 3_600_000).toISOString();
  const seed = (): OnboardingScheduleDto => ({
    id: scheduledId,
    caseId: scheduledCaseId,
    employmentId: employee.id,
    status: "SCHEDULED",
    version: 0,
    employmentVersion: employee.version,
    appliedEmploymentRevision: employee.appliedRevision,
    template: structuredClone(template),
    assignees: {},
    targetDate: employee.terms.startDate,
    startsOn: "2026-10-02",
    companyTimezone: "Asia/Jakarta",
    scheduledFor: midnight("2026-10-02"),
    validUntil: midnight("2026-10-05"),
    attempt: 1,
    createdBy: lifecycleAccountId,
    createdAt: "2026-10-01T03:00:00Z",
    reason: "Joining plan reviewed",
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
    `**/api/v1/companies/${companyIds[0]}/employees/${scheduledEmployee}?*`,
    async (route) => {
      expect(route.request().method()).toBe("GET");
      const url = new URL(route.request().url());
      employeeReads.push(url);
      expect(url.searchParams.get("asOf")).toMatch(/^\d{4}-\d{2}-\d{2}$/u);
      return route.fulfill({ json: structuredClone(employee) });
    },
  );
  await page.route("**/api/v1/companies/*/lifecycle/assignees?*", async (route) => {
    const url = new URL(route.request().url());
    assigneeReads.push(url);
    expect(url.searchParams.get("limit")).toBe("50");
    return route.fulfill({
      json: { items: [{ id: assigneeId, displayName: "Joining coordinator" }], nextCursor: null },
    });
  });
  await page.route(
    /\/api\/v1\/companies\/[^/]+\/employees\/[^/]+\/onboarding-schedules(?:[/?].*)?$/u,
    async (route) => {
      const request = route.request(),
        url = new URL(request.url()),
        parts = url.pathname.split("/"),
        company = parts[4] ?? "",
        employmentId = parts[6],
        id = parts[8],
        action = parts[9] ?? "schedule";
      const reject = (code: string, status = 409) =>
        route.fulfill({
          status,
          json: { code, fields: {}, parameters: {}, detail: "PRIVATE ONBOARDING DIAGNOSTIC" },
        });
      if (!onboardingPermissions.every((p) => permissions.includes(p)))
        return reject("onboarding_access_required", 403);
      if (company !== companyIds[0] || employmentId !== scheduledEmployee)
        return reject("employee_not_found", 404);
      if (request.method() === "GET") {
        reads.push(url);
        if (readFailure) return reject(readFailure, 403);
        let payload: unknown;
        if (id === "review") {
          expect([...url.searchParams.keys()].sort()).toEqual([
            "startsOn",
            "targetDate",
            "templateId",
          ]);
          const selected = api.templates
            .get(company)
            ?.find((t) => t.id === url.searchParams.get("templateId"));
          if (!selected) return reject("lifecycle_template_not_found", 404);
          const startsOn = url.searchParams.get("startsOn") ?? "";
          const review: OnboardingScheduleReviewDto = {
            employee: {
              id: employee.id,
              name: employee.person.legalName,
              employeeNumber: employee.employeeNumber,
            },
            employmentVersion: employee.version,
            appliedEmploymentRevision: employee.appliedRevision,
            template: structuredClone(selected),
            targetDate: employee.terms.startDate,
            startsOn,
            companyTimezone: "Asia/Jakarta",
            today: "2026-10-01",
            scheduledFor: midnight(startsOn),
            validUntil: midnight("2026-10-05"),
            basisRevision: basis,
            attempts: items.length,
            blockers,
          };
          payload = review;
        } else payload = id ? items.find((item) => item.id === id) : { items, nextCursor: null };
        if (!payload) return reject("onboarding_schedule_not_found", 404);
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
          ? [
              "assignees",
              "basisRevision",
              "caseId",
              "employmentVersion",
              "id",
              "reason",
              "startsOn",
              "targetDate",
              "templateId",
              "templateVersion",
            ]
          : ["expectedJobVersion", "expectedVersion", "reason"],
      );
      writes.push({ company, operation, action, body });
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
        expect(JSON.stringify(body)).toBe(replay.body);
        expect(action).toBe(replay.action);
        return route.fulfill({ json: replay.receipt });
      }
      let receipt: { id: string; version: number };
      if (action === "schedule") {
        const selected = api.templates.get(company)?.find((t) => t.id === body.templateId);
        if (!selected) return reject("lifecycle_template_not_found", 404);
        if (
          body.basisRevision !== basis ||
          body.employmentVersion !== employee.version ||
          body.templateVersion !== selected?.version
        )
          return reject("onboarding_schedule_changed");
        if (items.some((item) => item.status === "SCHEDULED"))
          return reject("onboarding_schedule_active");
        if (blockers[0]) return reject(blockers[0]);
        const schedule: OnboardingScheduleDto = {
          ...seed(),
          id: String(body.id),
          caseId: String(body.caseId),
          template: structuredClone(selected),
          assignees: body.assignees as Record<string, string>,
          reason: String(body.reason),
          targetDate: String(body.targetDate),
          startsOn: String(body.startsOn),
          scheduledFor: midnight(String(body.startsOn)),
          attempt: items.length + 1,
        };
        items.push(schedule);
        receipt = { id: schedule.id, version: 0 };
      } else {
        const schedule = items.find((item) => item.id === id);
        if (!schedule) return reject("onboarding_schedule_not_found", 404);
        if (
          schedule.version !== body.expectedVersion ||
          schedule.job.version !== body.expectedJobVersion
        )
          return reject("stale_version");
        if (!schedule.actions.includes(action === "cancel" ? "cancel" : "recover"))
          return reject("onboarding_schedule_finished");
        if (action === "cancel") {
          schedule.job.cancellationRequested = true;
          schedule.job.version++;
          schedule.actions = [];
          receipt = { id: schedule.job.id, version: schedule.job.version };
        } else {
          schedule.status = schedule.job.status === "CANCELLED" ? "CANCELLED" : "FAILED";
          schedule.version = 1;
          schedule.finishedAt = "2026-10-02T02:00:00Z";
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
    employee,
    employeeReads,
    assigneeReads,
    scheduleReads: reads,
    writes,
    seed,
    get committed() {
      return committed;
    },
    setBlockers: (values: string[]) => {
      blockers = values;
    },
    changeBasis: () => {
      basis = "b".repeat(64);
      employee.version++;
      template.version++;
    },
    rejectReads: (code: string | null) => {
      readFailure = code;
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
        version: 4,
        attempts: 4,
        finishedAt: "2026-10-02T01:00:00Z",
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
    finishInitiation: () => {
      const item = items[0];
      if (!item) throw new Error("Expected schedule");
      item.status = "STARTED";
      item.version = 1;
      item.finishedAt = "2026-10-02T01:00:00Z";
      item.job = {
        ...item.job,
        status: "SUCCEEDED",
        version: 2,
        attempts: 1,
        completedItems: 1,
        finishedAt: item.finishedAt,
      };
      item.actions = [];
      api.cases.get(companyIds[0])?.set(item.caseId, {
        id: item.caseId,
        employmentId: employee.id,
        employee: {
          id: employee.id,
          name: employee.person.legalName,
          employeeNumber: employee.employeeNumber,
        },
        kind: "ONBOARDING",
        targetDate: item.targetDate,
        templateId: item.template.id,
        templateVersion: item.template.version,
        templateName: item.template.name,
        status: "OPEN",
        version: 0,
        createdBy: item.createdBy,
        createdAt: item.finishedAt,
        tasks: item.template.tasks.map((task) => ({
          key: task.key,
          title: task.title,
          required: task.required,
          dueDate: new Date(Date.parse(`${item.targetDate}T00:00:00Z`) + task.dueDays * 86_400_000)
            .toISOString()
            .slice(0, 10),
          assigneeId: Object.hasOwn(item.assignees, task.key)
            ? (item.assignees[task.key] ?? null)
            : null,
          status: "PENDING",
          completedBy: null,
          completedAt: null,
        })),
      });
    },
  };
}
