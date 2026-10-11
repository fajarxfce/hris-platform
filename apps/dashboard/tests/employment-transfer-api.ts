import type { Page } from "@playwright/test";
import type { EmploymentTransferInputDto } from "../src/features/people/data/models/employment-transfer-dto";
import { employmentId, employmentManagerId, installEmploymentApi } from "./employment-api";
import { companyIds } from "./identity-api";

export async function installEmploymentTransferApi(
  page: Page,
  permissions = ["company.read", "people.read", "people.manage", "people.transfer"],
) {
  const base = await installEmploymentApi(page, permissions);
  const reads: URL[] = [];
  const writes: {
    operation: string;
    csrf: string | undefined;
    body: EmploymentTransferInputDto;
  }[] = [];
  const receipts = new Map<string, { payload: string; id: string; version: number }>();
  const transfers: unknown[] = [];
  let targetPermissions = ["company.read", "people.read", "people.manage", "people.transfer"];
  let date = "2026-10-01";
  let version = 7;
  let rejection: string | null = null;
  let reviewFailure: string | null = null;
  let lost = false;
  let held: { entered: () => void; wait: Promise<void> } | null = null;
  let heldTransfer: { entered: () => void; wait: Promise<void> } | null = null;
  await page.route("**/api/v1/companies/*/employees/*/transfer**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const company = url.pathname.split("/")[4];
    const id = url.pathname.split("/")[6];
    const action = url.pathname.split("/")[7];
    if (company !== companyIds[0] || id !== employmentId)
      return route.fulfill({ status: 404, json: { code: "employee_not_found" } });
    if (request.method() === "GET") {
      reads.push(url);
      if (action === "transfers") return route.fulfill({ json: transfers });
      if (action !== "transfer-review") return route.fallback();
      if (reviewFailure)
        return route.fulfill({
          status: 403,
          json: { code: reviewFailure, parameters: { companyId: companyIds[1] } },
        });
      const snapshot = {
        employee: {
          id: employmentId,
          companyId: companyIds[0],
          employeeNumber: "EMP-001",
          person: { legalName: "Alya Pratama", email: null },
          version,
          appliedRevision: 0,
          terms: {
            effectiveFrom: "2026-01-01",
            startDate: "2026-01-01",
            endDate: null,
            contract: "PERMANENT",
            status: "ACTIVE",
            branchId: "30000000-0000-4000-8000-000000000001",
            departmentId: null,
            positionId: null,
            costCenterId: null,
            managerId: employmentManagerId,
          },
        },
        sourceCompany: {
          id: companyIds[0],
          code: "NORTH",
          name: "North Company",
          timezone: "Asia/Jakarta",
        },
        targetCompany: {
          id: companyIds[1],
          code: "SOUTH",
          name: "South Company",
          timezone: "Asia/Jakarta",
        },
        sourceDate: date,
        targetDate: date,
        evaluatedAt: `${date}T06:00:00Z`,
        targetPermissions,
      };
      const waiting = held;
      held = null;
      if (waiting) {
        waiting.entered();
        await waiting.wait;
      }
      return route.fulfill({ json: snapshot });
    }
    if (request.method() !== "POST" || action !== "transfer") return route.fallback();
    const body = request.postDataJSON() as EmploymentTransferInputDto;
    const operation = request.headers()["idempotency-key"] ?? "";
    writes.push({ body, operation, csrf: request.headers()["x-csrf-token"] });
    const waiting = heldTransfer;
    heldTransfer = null;
    if (waiting) {
      waiting.entered();
      await waiting.wait;
    }
    if (rejection) {
      const code = rejection;
      rejection = null;
      return route.fulfill({ status: 409, json: { code, detail: "PRIVATE TECHNICAL ERROR" } });
    }
    const existing = receipts.get(operation);
    const payload = JSON.stringify(body);
    if (existing)
      return route.fulfill(
        existing.payload === payload
          ? { json: { id: existing.id, version: existing.version } }
          : { status: 409, json: { code: "idempotency_conflict" } },
      );
    if (body.expectedVersion !== version)
      return route.fulfill({ status: 409, json: { code: "stale_version" } });
    if (body.terms.effectiveFrom !== date)
      return route.fulfill({
        status: 422,
        json: { code: "transfer_requires_current_company_date" },
      });
    const receipt = { id: body.targetEmploymentId, version: 0 };
    receipts.set(operation, { ...receipt, payload });
    transfers.push({
      id: operation,
      sourceCompanyId: companyIds[0],
      sourceEmploymentId: employmentId,
      targetCompanyId: companyIds[1],
      targetEmploymentId: body.targetEmploymentId,
      sourceVersion: version + 1,
      effectiveDate: date,
      reason: body.reason,
      recordedAt: `${date}T06:00:00Z`,
    });
    if (lost) {
      lost = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    ...base,
    transferReads: reads,
    transferWrites: writes,
    get transfers() {
      return transfers;
    },
    loseTransfer: () => {
      lost = true;
    },
    rejectTransfer: (code: string) => {
      rejection = code;
    },
    failReview: (code: string | null) => {
      reviewFailure = code;
    },
    targetPermissions: (value: string[]) => {
      targetPermissions = value;
    },
    advanceReview: () => {
      version = 9;
      date = "2026-10-02";
    },
    holdReview: () => {
      let enter!: () => void;
      let release!: () => void;
      const entered = new Promise<void>((done) => {
        enter = done;
      });
      const wait = new Promise<void>((done) => {
        release = done;
      });
      held = { entered: enter, wait };
      return { entered, release: () => release() };
    },
    holdTransfer: () => {
      let enter!: () => void;
      let release!: () => void;
      const entered = new Promise<void>((done) => {
        enter = done;
      });
      const wait = new Promise<void>((done) => {
        release = done;
      });
      heldTransfer = { entered: enter, wait };
      return { entered, release: () => release() };
    },
  };
}
