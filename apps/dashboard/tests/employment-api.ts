import type { Page } from "@playwright/test";
import type { EmploymentChangeDto } from "../src/features/people/data/models/employment-change-dto";
import type { AssignedEmploymentTermsDto } from "../src/features/people/data/models/employment-details-dto";
import { companyIds, installIdentityApi } from "./identity-api";

export const employmentId = "40000000-0000-4000-8000-000000000001";
export const employmentManagerId = "40000000-0000-4000-8000-000000000002";
export const replacementBranchId = "30000000-0000-4000-8000-000000000002";
const oldBranch = {
  id: "30000000-0000-4000-8000-000000000001",
  code: "OLD",
  name: "Former branch",
  active: false,
};
const newBranch = { id: replacementBranchId, code: "HQ", name: "Head Office", active: true };
const initialTerms: AssignedEmploymentTermsDto = {
  effectiveFrom: "2026-01-01",
  startDate: "2026-01-01",
  endDate: null,
  contract: "PERMANENT",
  status: "ACTIVE",
  branchId: oldBranch.id,
  departmentId: null,
  positionId: "30000000-0000-4000-8000-000000000003",
  costCenterId: null,
  managerId: employmentManagerId,
};
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
export async function installEmploymentApi(
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
  const histories = companyIds.map(() => [
    {
      revision: 0,
      terms: initialTerms,
      reason: "Initial employment",
      recordedAt: "2026-01-01T00:00:00Z",
      cancellation: null,
    },
    {
      revision: 7,
      terms: { ...initialTerms, effectiveFrom: "2027-06-01" },
      reason: "Existing scheduled revision",
      recordedAt: "2026-09-01T00:00:00Z",
      cancellation: null,
    },
  ]);
  const versions = [7, 7];
  const reads: URL[] = [];
  const writes: {
    company: string;
    operation: string | undefined;
    csrf: string | undefined;
    body: EmploymentChangeDto;
  }[] = [];
  const receipts = new Map<string, { payload: string; receipt: { id: string; version: number } }>();
  let rejection: string | null = null;
  let lost = false;
  let readFailure: string | null = null;
  let held: { kind: "read" | "save"; gate: ReturnType<typeof gate> } | null = null;
  let commits = 0;
  await page.route("**/api/v1/companies/*/organization-units**", async (route) => {
    const url = new URL(route.request().url());
    reads.push(url);
    return route.fulfill({
      json: {
        items:
          url.searchParams.get("kind") === "BRANCH"
            ? [
                {
                  ...newBranch,
                  kind: "BRANCH",
                  parentId: null,
                  timezone: "Asia/Jakarta",
                  version: 0,
                },
              ]
            : [],
        nextCursor: null,
      },
    });
  });
  await page.route("**/api/v1/companies/*/employees**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const company = url.pathname.split("/")[4] ?? "";
    const id = url.pathname.split("/")[6];
    const action = url.pathname.split("/")[7];
    const index = (companyIds as readonly string[]).indexOf(company);
    const history = histories[index];
    const version = versions[index];
    if (!history || version === undefined || (id && id !== employmentId))
      return route.fulfill({ status: 404, json: { code: "employee_not_found" } });
    if (request.method() === "GET") {
      reads.push(url);
      const asOf = url.searchParams.get("asOf") ?? "2026-10-01";
      const applied =
        history
          .filter((item) => item.terms.effectiveFrom <= asOf)
          .toSorted(
            (a, b) =>
              b.terms.effectiveFrom.localeCompare(a.terms.effectiveFrom) || b.revision - a.revision,
          )[0] ?? history[0];
      if (!applied) throw new Error("Missing employment fixture");
      const employee = {
        id: employmentId,
        companyId: company,
        employeeNumber: "EMP-001",
        person: { legalName: index === 0 ? "Alya Pratama" : "Bayu Selatan", email: null },
        version,
        appliedRevision: applied.revision,
        terms: applied.terms,
      };
      const snapshot =
        action === "employment"
          ? {
              asOf,
              employee,
              branch:
                employee.terms.branchId === oldBranch.id
                  ? oldBranch
                  : employee.terms.branchId === newBranch.id
                    ? newBranch
                    : null,
              department: null,
              position: null,
              costCenter: null,
              manager: employee.terms.managerId
                ? {
                    id: employmentManagerId,
                    employeeNumber: "MGR-001",
                    legalName: "Manager One",
                    working: true,
                  }
                : null,
            }
          : action === "history"
            ? { items: history.toSorted((a, b) => b.revision - a.revision), nextCursor: null }
            : id
              ? employee
              : {
                  items: [
                    employee,
                    {
                      ...employee,
                      id: employmentManagerId,
                      employeeNumber: "MGR-001",
                      person: { legalName: "Manager One", email: null },
                      version: 0,
                      appliedRevision: 0,
                      terms: initialTerms,
                    },
                  ],
                  nextCursor: null,
                };
      const response = structuredClone(snapshot);
      const waiting = held?.kind === "read" ? held.gate : null;
      if (waiting) {
        held = null;
        waiting.enter();
        await waiting.held;
      }
      if (readFailure) return route.fulfill({ status: 503, json: { code: readFailure } });
      return route.fulfill({ json: response });
    }
    if (request.method() !== "POST" || action !== "revisions") return route.fallback();
    const body = request.postDataJSON() as EmploymentChangeDto;
    const operation = request.headers()["idempotency-key"];
    writes.push({ company, operation, csrf: request.headers()["x-csrf-token"], body });
    if (rejection) {
      const code = rejection;
      rejection = null;
      return route.fulfill({ status: 409, json: { code, detail: "PRIVATE TECHNICAL ERROR" } });
    }
    const key = `${company}:${operation}`;
    const payload = JSON.stringify(body);
    let saved = receipts.get(key);
    if (saved && saved.payload !== payload)
      return route.fulfill({ status: 409, json: { code: "operation_payload_mismatch" } });
    if (!saved) {
      if (body.version !== version)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      versions[index] = version + 1;
      history.push({
        revision: version + 1,
        terms: body.terms,
        reason: body.reason,
        recordedAt: "2026-10-01T00:00:00Z",
        cancellation: null,
      });
      saved = { payload, receipt: { id: employmentId, version: version + 1 } };
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
    loseNext: () => {
      lost = true;
    },
    rejectNext: (code: string) => {
      rejection = code;
    },
    failReads: (code: string | null) => {
      readFailure = code;
    },
    advanceVersion: () => {
      versions[0] = 9;
    },
    holdNext: (kind: "read" | "save") => {
      const waiting = gate();
      held = { kind, gate: waiting };
      return waiting;
    },
  };
}
