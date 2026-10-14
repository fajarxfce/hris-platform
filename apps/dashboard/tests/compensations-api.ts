import { expect, type Page } from "@playwright/test";
import type { CompensationChangeDto } from "../src/features/payroll/data/models/compensation-change-dto";
import {
  type CompensationReviewDto,
  employeeCompensationDto,
} from "../src/features/payroll/data/models/compensation-dto";
import {
  compensationActor,
  compensationEmployee,
  compensationPage,
  compensationRecord,
  compensationReference,
} from "./fixtures/compensations";
import { companyIds, installIdentityApi } from "./identity-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export const compensationKey = (company: string, employee: string) => `${company}:${employee}`;
export async function installCompensationsApi(
  page: Page,
  permissions = ["payroll.compensation.manage"],
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const records = new Map<string, CompensationReviewDto>([
    [compensationKey(companyIds[0], compensationEmployee), compensationRecord()],
  ]);
  for (let i = 2; i <= 23; i++) {
    const employee = {
      ...compensationReference,
      id: `ca000000-0000-4000-8000-${String(i).padStart(12, "0")}`,
      employeeNumber: `EMP-${String(i).padStart(3, "0")}`,
      name: `Upcoming Employee ${i}`,
    };
    records.set(compensationKey(companyIds[0], employee.id), {
      asOf: "2026-06",
      employee,
      current: null,
      effective: null,
      canChange: true,
      history: { items: [], nextCursor: null },
    });
  }
  const other = {
    ...compensationReference,
    id: "cb000000-0000-4000-8000-000000000001",
    name: "Bima Santoso",
  };
  records.set(compensationKey(companyIds[1], other.id), {
    asOf: "2026-06",
    employee: other,
    current: null,
    effective: null,
    canChange: true,
    history: { items: [], nextCursor: null },
  });
  const reads: URL[] = [],
    writes: {
      company: string;
      employee: string;
      operation: string;
      body: CompensationChangeDto;
    }[] = [];
  const receipts = new Map<string, { id: string; version: number; payload: string }>();
  let failure: string | null = null,
    drop = false,
    held: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null = null;
  function commit(company: string, employee: string, body: CompensationChangeDto) {
    const old = records.get(compensationKey(company, employee));
    if (!old) throw new Error("Missing fixture employee");
    const version = (old.current?.version ?? -1) + 1;
    const current = employeeCompensationDto.parse({
      employeeId: employee,
      employeeNumber: old.employee.employeeNumber,
      employeeName: old.employee.name,
      version,
      appliedRevision: version,
      currency: "IDR",
      effectiveFrom: body.effectiveFrom,
      terms: body.terms,
    });
    records.set(compensationKey(company, employee), {
      ...old,
      current,
      history: {
        items: [
          ...old.history.items.map((row) => ({
            ...row,
            compensation: { ...row.compensation, version },
          })),
          {
            compensation: structuredClone(current),
            actorId: compensationActor,
            reason: body.reason,
            recordedAt: "2026-10-14T08:00:00Z",
          },
        ],
        nextCursor: null,
      },
    });
    return { id: employee, version };
  }
  await page.route("**/api/v1/companies/*/payroll/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      company = url.pathname.split("/")[4] ?? "",
      employee = url.pathname.split("/")[7] ?? "";
    expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
    if (request.method() === "GET") {
      reads.push(url);
      const wait = held;
      if (wait) {
        held = null;
        wait.entered.resolve();
        await wait.released.promise;
      }
      if (failure) {
        const code = failure;
        failure = null;
        return route.fulfill({
          status: 403,
          json: { code, detail: "PRIVATE DIAGNOSTIC", fields: {}, parameters: {} },
        });
      }
      if (url.pathname.endsWith("/compensation-employees")) {
        expect(url.searchParams.get("limit")).toBe("20");
        const query = (url.searchParams.get("query") ?? "").toLowerCase(),
          after = url.searchParams.get("after");
        const items = [...records.entries()]
          .filter(([key]) => key.startsWith(`${company}:`))
          .map(([, record]) => record.employee)
          .filter(
            (employee) =>
              (employee.name.toLowerCase().includes(query) ||
                employee.employeeNumber.toLowerCase().includes(query)) &&
              (after === null || employee.employeeNumber > after),
          )
          .sort((a, b) => a.employeeNumber.localeCompare(b.employeeNumber));
        return route.fulfill({
          json: {
            items: items.slice(0, 20),
            nextCursor: items.length > 20 ? items[19]?.employeeNumber : null,
          },
        });
      }
      expect(url.pathname.endsWith("/compensation/review")).toBe(true);
      expect(url.searchParams.get("historyLimit")).toBe("20");
      const record = records.get(compensationKey(company, employee));
      if (!record) return route.fulfill({ status: 404, json: { code: "employee_not_found" } });
      return route.fulfill({
        json: compensationPage(
          record,
          url.searchParams.get("asOf") ?? "",
          url.searchParams.get("historyAfter"),
        ),
      });
    }
    expect(request.method()).toBe("PUT");
    expect(url.pathname.endsWith("/compensation")).toBe(true);
    const operation = request.headers()["idempotency-key"] ?? "",
      body = request.postDataJSON() as CompensationChangeDto;
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    writes.push({ company, employee, operation, body });
    if (failure) {
      const code = failure;
      failure = null;
      return route.fulfill({ status: 409, json: { code } });
    }
    const previous = receipts.get(`${company}:${operation}`);
    if (previous) {
      expect(JSON.stringify(body)).toBe(previous.payload);
      return route.fulfill({ json: { id: previous.id, version: previous.version } });
    }
    const record = records.get(compensationKey(company, employee));
    if (!record) return route.fulfill({ status: 404, json: { code: "employee_not_found" } });
    if (!record.canChange)
      return route.fulfill({ status: 403, json: { code: "self_compensation_change_denied" } });
    if (record.employee.employmentVersion !== body.expectedEmploymentVersion)
      return route.fulfill({ status: 409, json: { code: "stale_employment_version" } });
    if ((record.current?.version ?? null) !== body.expectedVersion)
      return route.fulfill({ status: 409, json: { code: "stale_version" } });
    const receipt = commit(company, employee, body);
    receipts.set(`${company}:${operation}`, { ...receipt, payload: JSON.stringify(body) });
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    identity,
    records,
    reads,
    writes,
    failNext: (code: string) => {
      failure = code;
    },
    dropNext: () => {
      drop = true;
    },
    holdRead: () => {
      const entered = gate(),
        released = gate();
      held = { entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
    advanceEmployment: () => {
      const record = records.get(compensationKey(companyIds[0], compensationEmployee));
      if (!record) throw new Error("Missing employee fixture");
      record.employee.employmentVersion++;
    },
  };
}
