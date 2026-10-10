import type { Page } from "@playwright/test";
import type { OrganizationUnitDto } from "../src/features/organization/data/models/organization-unit-dto";
import type { EmployeeCreationDto } from "../src/features/people/data/models/employee-creation-dto";
import type { EmployeeDto } from "../src/features/people/data/models/employee-dto";
import { companyIds, installIdentityApi } from "./identity-api";

export const managerId = "40000000-0000-4000-8000-000000000001";
function gate() {
  let enter!: () => void;
  let release!: () => void;
  return {
    entered: new Promise<void>((done) => {
      enter = done;
    }),
    held: new Promise<void>((done) => {
      release = done;
    }),
    enter: () => enter(),
    release: () => release(),
  };
}
export async function installEmployeeCreationApi(
  page: Page,
  permissions = ["company.read", "people.read", "people.manage"],
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const units: OrganizationUnitDto[] = [
    ...Array.from(
      { length: 55 },
      (_, index): OrganizationUnitDto => ({
        id: `30000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
        code: `B${String(index + 1).padStart(3, "0")}`,
        name: `Branch ${String(index + 1).padStart(3, "0")}`,
        kind: "BRANCH",
        active: true,
        timezone: "Asia/Jakarta",
        parentId: null,
        version: 0,
      }),
    ),
    ...(["DEPARTMENT", "POSITION", "COST_CENTER"] as const).map(
      (kind, index): OrganizationUnitDto => ({
        id: `31000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
        code: kind,
        name: `Selected ${kind}`,
        kind,
        active: true,
        timezone: null,
        parentId: null,
        version: 0,
      }),
    ),
  ];
  const employees = new Map<string, EmployeeDto>();
  for (const companyId of companyIds)
    employees.set(`${companyId}:${managerId}`, {
      id: managerId,
      companyId,
      employeeNumber: "MGR-001",
      person: { legalName: "Manager One", email: null },
      version: 0,
      appliedRevision: 0,
      terms: {
        effectiveFrom: "2026-01-01",
        startDate: "2026-01-01",
        endDate: null,
        contract: "PERMANENT",
        status: "ACTIVE",
      },
    });
  const reads: URL[] = [];
  const writes: {
    company: string;
    operation: string | undefined;
    csrf: string | undefined;
    body: EmployeeCreationDto;
  }[] = [];
  const receipts = new Map<string, { payload: string; receipt: { id: string; version: number } }>();
  let rejection: { code: string; fields: Record<string, string> } | null = null;
  let lost = false;
  let detailFailure: string | null = null;
  let held: { kind: "units" | "save"; gate: ReturnType<typeof gate> } | null = null;
  let commits = 0;
  await page.route("**/api/v1/companies/*/organization-units**", async (route) => {
    const url = new URL(route.request().url());
    reads.push(url);
    const query = url.searchParams;
    const matches = units.filter(
      (unit) =>
        unit.kind === query.get("kind") &&
        unit.active &&
        `${unit.name} ${unit.code}`
          .toLowerCase()
          .includes((query.get("query") ?? "").toLowerCase()) &&
        (!query.has("after") || `${unit.kind}:${unit.code}` > (query.get("after") ?? "")),
    );
    const items = matches.slice(0, 50);
    const waiting = held?.kind === "units" ? held.gate : null;
    if (waiting) {
      held = null;
      waiting.enter();
      await waiting.held;
    }
    return route.fulfill({
      json: {
        items,
        nextCursor: matches.length > 50 ? `${items[49]?.kind}:${items[49]?.code}` : null,
      },
    });
  });
  await page.route("**/api/v1/companies/*/employees**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const company = url.pathname.split("/")[4] ?? "";
    const id = url.pathname.split("/")[6];
    if (request.method() === "GET") {
      reads.push(url);
      if (id) {
        if (detailFailure) return route.fulfill({ status: 503, json: { code: detailFailure } });
        const employee = employees.get(`${company}:${id}`);
        return employee
          ? route.fulfill({ json: employee })
          : route.fulfill({ status: 404, json: { code: "employee_not_found" } });
      }
      const query = url.searchParams.get("query")?.toLowerCase() ?? "";
      return route.fulfill({
        json: {
          items: [...employees.values()].filter(
            (employee) =>
              employee.companyId === company &&
              `${employee.person.legalName} ${employee.employeeNumber}`
                .toLowerCase()
                .includes(query),
          ),
          nextCursor: null,
        },
      });
    }
    if (request.method() !== "POST" || id) return route.fallback();
    const operation = request.headers()["idempotency-key"];
    const body = request.postDataJSON() as EmployeeCreationDto;
    writes.push({ company, operation, csrf: request.headers()["x-csrf-token"], body });
    if (rejection) {
      const failure = rejection;
      rejection = null;
      return route.fulfill({
        status: 400,
        json: { ...failure, detail: "PRIVATE TECHNICAL ERROR" },
      });
    }
    const key = `${company}:${operation}`;
    const payload = JSON.stringify(body);
    let saved = receipts.get(key);
    if (saved && saved.payload !== payload)
      return route.fulfill({ status: 409, json: { code: "operation_payload_mismatch" } });
    if (!saved) {
      employees.set(`${company}:${body.id}`, {
        id: body.id,
        companyId: company,
        employeeNumber: body.employeeNumber,
        person: { legalName: body.person.legalName, email: body.person.email },
        terms: body.terms,
        version: 0,
        appliedRevision: 0,
      });
      saved = { payload, receipt: { id: body.id, version: 0 } };
      receipts.set(key, saved);
      commits += 1;
    }
    const waiting = held?.kind === "save" ? held.gate : null;
    if (waiting) {
      held = null;
      waiting.enter();
      await waiting.held;
    }
    if (lost) {
      lost = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: saved.receipt });
  });
  return {
    identity,
    reads,
    writes,
    get commits() {
      return commits;
    },
    rejectNext: (code: string, fields: Record<string, string> = {}) => {
      rejection = { code, fields };
    },
    loseNext: () => {
      lost = true;
    },
    failDetails: (code: string) => {
      detailFailure = code;
    },
    holdNext: (kind: "units" | "save") => {
      const waiting = gate();
      held = { kind, gate: waiting };
      return waiting;
    },
  };
}
