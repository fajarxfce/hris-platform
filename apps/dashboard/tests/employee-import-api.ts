import { expect, type Page } from "@playwright/test";
import type {
  EmployeeImportDto,
  EmployeeImportRowDto,
} from "../src/features/people/data/models/employee-import-dto";
import { companyIds, installIdentityApi } from "./identity-api";

export const employeeImportPermissions = [
  "people.import",
  "people.manage",
  "people.profile.read",
  "people.profile.manage",
];
export const importId = (company: number, index: number) =>
  `30000000-0000-4000-8000-${String(company * 100 + index).padStart(12, "0")}`;
const actor = "20000000-0000-4000-8000-000000000001";
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
export async function installEmployeeImportApi(
  page: Page,
  permissions = employeeImportPermissions,
) {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const records = new Map<string, EmployeeImportDto[]>(
    companyIds.map((company, companyIndex) => [
      company,
      Array.from({ length: companyIndex === 0 ? 12 : 1 }, (_, i) => ({
        id: importId(companyIndex, i + 1),
        fileName: `${companyIndex === 0 ? "north" : "south"}-${i + 1}.csv`,
        sourceHash: "a".repeat(64),
        rowCount: 27,
        status: "REVIEW",
        jobId: importId(companyIndex, i + 51),
        version: 1,
        createdBy: actor,
        createdAt: "2026-10-01T00:00:00Z",
        reason: "Monthly intake",
      })),
    ]),
  );
  const rowValues = (company: number): EmployeeImportRowDto[] =>
    Array.from({ length: 27 }, (_, index) => {
      const employeeNumber = `EMP${String(index + 1).padStart(3, "0")}`;
      const legalName = `${company === 0 ? "North" : "South"} employee ${index + 1}`;
      return {
        number: index + 1,
        employeeNumber,
        legalName,
        status: index < 2 ? "INVALID" : "READY",
        issues:
          index === 0
            ? { startDate: "invalid_date" }
            : index === 1
              ? { terms: "employment_date_outside_import_range" }
              : {},
        proposed:
          index === 0
            ? null
            : {
                employeeId: importId(company, 200 + index),
                employeeNumber,
                legalName,
                nationality: "ID",
                birthDate: "1990-01-01",
                email: "private@example.invalid",
                terms: {
                  startDate: index === 1 ? "+10000-01-01" : "2026-01-01",
                  effectiveFrom: index === 1 ? "+10000-01-01" : "2026-01-01",
                  endDate: null,
                  contract: "PERMANENT",
                  status: "ACTIVE",
                  branchId: null,
                  departmentId: null,
                  positionId: null,
                  costCenterId: null,
                  managerId: null,
                },
              },
        createdEmploymentId: null,
      };
    });
  const reads: URL[] = [];
  let held: { kind: string; gate: ReturnType<typeof gate> } | null = null;
  let failure: { kind: string; code: string; status: number } | null = null;
  await page.route("**/api/v1/companies/*/employee-imports**", async (route) => {
    expect(route.request().method()).toBe("GET");
    const url = new URL(route.request().url());
    reads.push(url);
    const parts = url.pathname.split("/");
    const company = parts[4] ?? "";
    const id = parts[6];
    const kind = parts[7] ?? (id ? "summary" : "list");
    const all = records.get(company) ?? [];
    const record = all.find((item) => item.id === id);
    if (id && !record)
      return route.fulfill({
        status: 404,
        json: { code: "employee_import_not_found", fields: {}, parameters: {} },
      });
    if (failure?.kind === kind)
      return route.fulfill({
        status: failure.status,
        json: {
          code: failure.code,
          fields: {},
          parameters: {},
          detail: "PRIVATE TECHNICAL MESSAGE",
        },
      });
    const limit = Number(url.searchParams.get("limit"));
    const after = url.searchParams.get("after");
    let payload: unknown;
    if (kind === "list") {
      expect(limit).toBe(10);
      const remaining = all.filter((item) => after === null || item.id > after);
      payload = {
        items: remaining.slice(0, limit),
        nextCursor: remaining.length > limit ? remaining[limit - 1]?.id : null,
      };
    } else if (kind === "summary") payload = { batch: record, counts: { READY: 25, INVALID: 2 } };
    else if (kind === "rows") {
      expect(limit).toBe(25);
      const remaining = rowValues(company === companyIds[0] ? 0 : 1).filter(
        (item) => item.number > Number(after ?? 0),
      );
      payload = {
        items: remaining.slice(0, limit),
        nextCursor: remaining.length > limit ? String(remaining[limit - 1]?.number) : null,
      };
    } else {
      expect(kind).toBe("attempts");
      expect(limit).toBe(10);
      const attempts = Array.from({ length: 11 }, (_, index) => ({
        jobId: importId(0, 300 + index),
        phase: "PREVIEW",
        actorId: actor,
        createdAt: "2026-10-01T00:00:00Z",
      })).filter((item) => after === null || item.jobId > after);
      payload = {
        items: attempts.slice(0, limit),
        nextCursor: attempts.length > limit ? attempts[limit - 1]?.jobId : null,
      };
    }
    const snapshot = structuredClone(payload);
    if (held?.kind === kind) {
      const pending = held.gate;
      held = null;
      pending.enter();
      await pending.held;
    }
    return route.fulfill({ json: snapshot });
  });
  return {
    identity,
    reads,
    records,
    hold: (kind: string) => {
      const pending = gate();
      held = { kind, gate: pending };
      return pending;
    },
    fail: (kind: string, code: string, status = 403) => {
      failure = { kind, code, status };
    },
    recover: () => {
      failure = null;
    },
  };
}
