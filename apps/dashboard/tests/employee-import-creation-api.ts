import { expect, type Page } from "@playwright/test";
import type { EmployeeImportStartDto } from "../src/features/people/data/models/employee-import-start-dto";
import { employeeImportPermissions, installEmployeeImportApi } from "./employee-import-api";

export const employeeCsvTemplate = "employee_number,legal_name,nationality,start_date,contract\r\n";
export const employeeCsv = `${employeeCsvTemplate}IMP01,"Preview, Employee",ID,2026-01-01,PERMANENT\r\nIMP02,Second Employee,ID,invalid,PERMANENT\r\n`;
function gate() {
  let enter!: () => void;
  let release!: () => void;
  const entered = new Promise<void>((yes) => {
    enter = yes;
  });
  const held = new Promise<void>((yes) => {
    release = yes;
  });
  return { enter, release, entered, held };
}
export async function installEmployeeImportCreationApi(
  page: Page,
  permissions = employeeImportPermissions,
) {
  const api = await installEmployeeImportApi(page, permissions);
  const starts: {
    company: string;
    operation: string;
    csrf: string | undefined;
    body: EmployeeImportStartDto;
  }[] = [];
  const templates: URL[] = [];
  const receipts = new Map<string, { body: string; receipt: { id: string; version: number } }>();
  let lose = false;
  let failure: { code: string; status: number } | null = null;
  let held: ReturnType<typeof gate> | null = null;
  let committed = 0;
  await page.route("**/api/v1/companies/*/employee-imports/template", async (route) => {
    expect(route.request().method()).toBe("GET");
    expect(route.request().headers().accept).toBe("text/csv");
    templates.push(new URL(route.request().url()));
    return route.fulfill({
      body: employeeCsvTemplate,
      headers: { "content-type": "text/csv;charset=UTF-8", "cache-control": "no-store" },
    });
  });
  await page.route("**/api/v1/companies/*/employee-imports", async (route) => {
    const request = route.request();
    if (request.method() !== "POST") return route.fallback();
    const company = new URL(request.url()).pathname.split("/")[4] ?? "";
    const operation = request.headers()["idempotency-key"] ?? "";
    const csrf = request.headers()["x-csrf-token"];
    const body = request.postDataJSON() as EmployeeImportStartDto;
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    expect(csrf).toBeTruthy();
    expect(Object.keys(body).sort()).toEqual(["csv", "fileName", "id", "reason"]);
    starts.push({ company, operation, csrf, body });
    const pending = held;
    held = null;
    if (pending) {
      pending.enter();
      await pending.held;
    }
    if (failure) {
      const denied = failure;
      failure = null;
      return route.fulfill({
        status: denied.status,
        json: { code: denied.code, fields: {}, parameters: {}, detail: "PRIVATE CSV DIAGNOSTIC" },
      });
    }
    const key = `${company}:${operation}`;
    const replay = receipts.get(key);
    if (replay) {
      expect(replay.body).toBe(JSON.stringify(body));
      return route.fulfill({ json: replay.receipt });
    }
    const records = api.records.get(company);
    if (!records) throw new Error("Expected fixture company");
    expect(body.csv).toBe(employeeCsv);
    records.push({
      id: body.id,
      fileName: body.fileName,
      rowCount: 2,
      sourceHash: "b".repeat(64),
      status: "PREVIEWING",
      jobId: "40000000-0000-4000-8000-000000000001",
      version: 0,
      createdBy: "20000000-0000-4000-8000-000000000001",
      createdAt: "2026-10-01T00:00:00Z",
      reason: body.reason,
    });
    records.sort((a, b) => a.id.localeCompare(b.id));
    api.contexts.set(body.id, {
      counts: { PENDING: 2 },
      jobStatus: "QUEUED",
      cancellationRequested: false,
      availableActions: ["cancel"],
    });
    const receipt = { id: body.id, version: 0 };
    receipts.set(key, { body: JSON.stringify(body), receipt });
    committed++;
    if (lose) {
      lose = false;
      return route.abort("connectionfailed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    ...api,
    starts,
    templates,
    get committed() {
      return committed;
    },
    lose: () => {
      lose = true;
    },
    reject: (code: string, status = 400) => {
      failure = { code, status };
    },
    holdStart: () => {
      const pending = gate();
      held = pending;
      return pending;
    },
  };
}
