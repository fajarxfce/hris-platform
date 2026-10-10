import { expect, type Page } from "@playwright/test";
import type { LifecycleCaseDto } from "../src/features/lifecycle/data/models/lifecycle-case-dto";
import type { LifecycleCaseStartDto } from "../src/features/lifecycle/data/models/lifecycle-case-start-dto";
import type { EmployeeDto } from "../src/features/people/data/models/employee-dto";
import { companyIds } from "./identity-api";
import { installLifecycleApi } from "./lifecycle-api";
import { lifecycleAccountId } from "./lifecycle-cases-api";

export const lifecycleEmployeeId = (company = 0) =>
  `e0000000-abcd-4000-8000-${company + 1}00000000001`;
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
export async function installLifecycleCaseCreationApi(
  page: Page,
  options: { permissions?: string[]; emptyTemplates?: boolean } = {},
) {
  const api = await installLifecycleApi(page, {
    permissions: options.permissions ?? [
      "people.read",
      "people.lifecycle.read",
      "people.lifecycle.manage",
    ],
    empty: options.emptyTemplates ?? false,
  });
  const employeeReads: URL[] = [];
  const caseReads: URL[] = [];
  const employees = new Map<string, EmployeeDto>(
    companyIds.map((companyId, index) => [
      companyId,
      {
        id: lifecycleEmployeeId(index),
        companyId,
        employeeNumber: "EMP-001",
        person: { legalName: `${index === 0 ? "North" : "South"} employee`, email: null },
        terms: {
          effectiveFrom: "2026-01-01",
          startDate: "2026-01-01",
          endDate: null,
          contract: "PERMANENT",
          status: "ACTIVE",
        },
        version: 0,
        appliedRevision: 0,
      },
    ]),
  );
  const cases = new Map<string, Map<string, LifecycleCaseDto>>(
    companyIds.map((company) => [company, new Map()]),
  );
  const creations: {
    company: string;
    operation: string;
    csrf: string | undefined;
    body: LifecycleCaseStartDto;
  }[] = [];
  const receipts = new Map<string, { body: string; receipt: { id: string; version: number } }>();
  let commits = 0;
  let lose = false;
  let employeeFailure: string | null = null;
  let failure: { code: string; status: number } | null = null;
  let pendingWrite: ReturnType<typeof gate> | null = null;
  await page.route("**/api/v1/companies/*/employees{,?*,/**}", async (route) => {
    const request = route.request();
    expect(request.method()).toBe("GET");
    const url = new URL(request.url());
    employeeReads.push(url);
    const company = url.pathname.split("/")[4] ?? "";
    const id = url.pathname.split("/")[6];
    const employee = employees.get(company);
    if (employeeFailure || !employee || (id && employee.id !== id))
      return route.fulfill({
        status: 404,
        json: {
          code: employeeFailure ?? "employee_not_found",
          fields: {},
          parameters: {},
          detail: "PRIVATE EMPLOYEE",
        },
      });
    expect(url.searchParams.get("asOf")).toMatch(/^\d{4}-\d{2}-\d{2}$/u);
    return route.fulfill({ json: id ? employee : { items: [employee], nextCursor: null } });
  });
  await page.route("**/api/v1/companies/*/lifecycle/cases{,?*,/**}", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const company = url.pathname.split("/")[4] ?? "";
    const id = url.pathname.split("/")[7];
    const reject = (code: string, status = 409) =>
      route.fulfill({
        status,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE LIFECYCLE" },
      });
    if (request.method() === "GET") {
      caseReads.push(url);
      const record = id ? cases.get(company)?.get(id) : null;
      if (!record) return reject("lifecycle_case_not_found", 404);
      return route.fulfill({ json: record });
    }
    expect(request.method()).toBe("POST");
    expect(id).toBeUndefined();
    const body = request.postDataJSON() as LifecycleCaseStartDto;
    const operation = request.headers()["idempotency-key"] ?? "";
    const csrf = request.headers()["x-csrf-token"];
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    expect(csrf).toBeTruthy();
    expect(Object.keys(body).sort()).toEqual([
      "assignees",
      "employmentId",
      "id",
      "reason",
      "targetDate",
      "templateId",
      "templateVersion",
    ]);
    creations.push({ company, operation, csrf, body });
    const waiting = pendingWrite;
    pendingWrite = null;
    if (waiting) {
      waiting.enter();
      await waiting.held;
    }
    if (failure) {
      const rejected = failure;
      failure = null;
      return reject(rejected.code, rejected.status);
    }
    const key = `${company}:${operation}`;
    const replay = receipts.get(key);
    if (replay) {
      expect(replay.body).toBe(JSON.stringify(body));
      return route.fulfill({ json: replay.receipt });
    }
    const employee = employees.get(company);
    if (!employee || employee.id !== body.employmentId) return reject("employee_not_found", 404);
    const template = api.templates.get(company)?.find((item) => item.id === body.templateId);
    if (!template?.active) return reject("lifecycle_template_unavailable", 422);
    if (template.version !== body.templateVersion) return reject("stale_template_version");
    const record: LifecycleCaseDto = {
      id: body.id,
      employmentId: body.employmentId,
      employee: {
        id: body.employmentId,
        name: employee.person.legalName,
        employeeNumber: employee.employeeNumber,
      },
      kind: template.kind,
      targetDate: body.targetDate,
      templateId: template.id,
      templateVersion: template.version,
      templateName: template.name,
      status: "OPEN",
      version: 0,
      createdBy: lifecycleAccountId,
      createdAt: "2026-10-01T00:00:00Z",
      tasks: template.tasks.map((task) => {
        const due = new Date(`${body.targetDate}T00:00:00Z`);
        due.setUTCDate(due.getUTCDate() + task.dueDays);
        return {
          key: task.key,
          title: task.title,
          required: task.required,
          dueDate: due.toISOString().slice(0, 10),
          assigneeId: null,
          status: "PENDING",
          completedBy: null,
          completedAt: null,
        };
      }),
    };
    cases.get(company)?.set(record.id, record);
    const receipt = { id: record.id, version: 0 };
    receipts.set(key, { body: JSON.stringify(body), receipt });
    commits++;
    if (lose) {
      lose = false;
      return route.abort("connectionfailed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    ...api,
    employeeReads,
    caseReads,
    creations,
    cases,
    get caseCommits() {
      return commits;
    },
    loseCreation: () => {
      lose = true;
    },
    rejectCreation: (code: string, status = 409) => {
      failure = { code, status };
    },
    failEmployee: (code: string | null) => {
      employeeFailure = code;
    },
    holdCreation: () => {
      const held = gate();
      pendingWrite = held;
      return held;
    },
  };
}
