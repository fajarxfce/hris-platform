import { expect, type Page } from "@playwright/test";
import type { EmployeeImportAction } from "../src/features/people/domain/entities/employee-import-change";
import { employeeImportPermissions, installEmployeeImportApi } from "./employee-import-api";

function gate() {
  let enter!: () => void;
  let release!: () => void;
  const entered = new Promise<void>((resolve) => {
    enter = resolve;
  });
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  return { enter, release, entered, held };
}
export async function installEmployeeImportTransitionApi(
  page: Page,
  permissions = employeeImportPermissions,
) {
  const api = await installEmployeeImportApi(page, permissions);
  const writes: {
    company: string;
    id: string;
    action: string;
    operation: string;
    csrf: string | undefined;
    body: { expectedVersion: number; reason: string; allowPartial?: boolean };
  }[] = [];
  const receipts = new Map<
    string,
    { id: string; action: string; body: string; receipt: { id: string; version: number } }
  >();
  let lose = false;
  let failure: { code: string; status: number } | null = null;
  let held: ReturnType<typeof gate> | null = null;
  let committed = 0;
  await page.route("**/api/v1/companies/*/employee-imports/*/*", async (route) => {
    const request = route.request();
    if (request.method() !== "POST") return route.fallback();
    const parts = new URL(request.url()).pathname.split("/");
    const company = parts[4] ?? "";
    const id = parts[6] ?? "";
    const action = parts[7] as EmployeeImportAction;
    expect(["apply", "resume", "cancel"]).toContain(action);
    const operation = request.headers()["idempotency-key"] ?? "";
    const csrf = request.headers()["x-csrf-token"];
    const body = request.postDataJSON() as {
      expectedVersion: number;
      reason: string;
      allowPartial?: boolean;
    };
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    expect(csrf).toBeTruthy();
    expect(Object.keys(body).sort()).toEqual(
      action === "apply"
        ? ["allowPartial", "expectedVersion", "reason"]
        : ["expectedVersion", "reason"],
    );
    writes.push({ company, id, action, operation, csrf, body });
    const pending = held;
    held = null;
    if (pending) {
      pending.enter();
      await pending.held;
    }
    const reject = (code: string, status = 409) =>
      route.fulfill({
        status,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE IMPORT" },
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
      expect(replay.action).toBe(action);
      expect(replay.body).toBe(JSON.stringify(body));
      return route.fulfill({ json: replay.receipt });
    }
    const record = api.records.get(company)?.find((item) => item.id === id);
    const context = api.contexts.get(id);
    if (!record || !context) return reject("employee_import_not_found", 404);
    if (record.version !== body.expectedVersion) return reject("stale_version");
    if (!context.availableActions.includes(action)) return reject("employee_import_is_terminal");
    if (action === "apply" && (context.counts.INVALID ?? 0) > 0 && !body.allowPartial)
      return reject("employee_import_has_invalid_rows");
    record.version++;
    if (action === "cancel") {
      context.cancellationRequested = true;
      if (context.jobStatus === "RUNNING" || context.jobStatus === "QUEUED")
        context.availableActions = [];
      else {
        record.status = "CANCELLED";
        context.availableActions = [];
      }
    } else {
      record.status = "IMPORTING";
      context.jobStatus = "QUEUED";
      context.cancellationRequested = false;
      context.availableActions = ["cancel"];
    }
    const receipt = { id, version: record.version };
    receipts.set(key, { id, action, body: JSON.stringify(body), receipt });
    committed++;
    if (lose) {
      lose = false;
      return route.abort("connectionfailed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    ...api,
    writes,
    get committed() {
      return committed;
    },
    lose: () => {
      lose = true;
    },
    reject: (code: string, status = 409) => {
      failure = { code, status };
    },
    holdWrite: () => {
      const pending = gate();
      held = pending;
      return pending;
    },
  };
}
