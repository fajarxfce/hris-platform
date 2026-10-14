import { expect, type Page } from "@playwright/test";
import type { PayrollPolicyChangeDto } from "../src/features/payroll/data/models/payroll-policy-change-dto";
import {
  type PayrollPolicyReviewDto,
  payrollPolicyDto,
} from "../src/features/payroll/data/models/payroll-policy-dto";
import {
  payrollActor,
  payrollPolicyPage,
  payrollPolicyRecord,
  payrollRule,
} from "./fixtures/payroll-policies";
import { companyIds, installIdentityApi } from "./identity-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installPayrollPoliciesApi(
  page: Page,
  permissions = ["payroll.policy.manage"],
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const records = new Map<string, PayrollPolicyReviewDto>([
    [companyIds[0], payrollPolicyRecord()],
    [companyIds[1], payrollPolicyRecord(0, "6200000.00")],
  ]);
  const reads: URL[] = [],
    writes: { company: string; operation: string; body: PayrollPolicyChangeDto }[] = [];
  const receipts = new Map<string, { id: string; version: number; payload: string }>();
  let failure: string | null = null,
    drop = false;
  let held: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null = null;
  function commit(company: string, body: PayrollPolicyChangeDto) {
    const old = records.get(company),
      version = (old?.current?.version ?? -1) + 1;
    const { reason, expectedVersion: _, ...terms } = body;
    const current = payrollPolicyDto.parse({
      ...terms,
      currency: "IDR",
      version,
      appliedRevision: version,
    });
    records.set(company, {
      asOf: current.effectiveFrom,
      current,
      effective: current,
      history: {
        items: [
          ...(old?.history.items ?? []).map((row) => ({
            ...row,
            policy: { ...row.policy, version },
          })),
          { policy: current, actorId: payrollActor, reason, recordedAt: "2026-10-14T08:00:00Z" },
        ],
        nextCursor: null,
      },
    });
    return { id: company, version };
  }
  await page.route("**/api/v1/companies/*/payroll/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      company = url.pathname.split("/")[4] ?? "";
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
      if (url.pathname.endsWith("/income-tax-rules")) return route.fulfill({ json: [payrollRule] });
      expect(url.pathname.endsWith("/policy/review")).toBe(true);
      expect(url.searchParams.get("historyLimit")).toBe("20");
      const month = url.searchParams.get("asOf") ?? "",
        record = records.get(company);
      return route.fulfill({
        json: record
          ? payrollPolicyPage(record, month, url.searchParams.get("historyAfter"))
          : {
              asOf: month,
              current: null,
              effective: null,
              history: { items: [], nextCursor: null },
            },
      });
    }
    expect(request.method()).toBe("PUT");
    expect(url.pathname.endsWith("/policy")).toBe(true);
    const operation = request.headers()["idempotency-key"] ?? "",
      body = request.postDataJSON() as PayrollPolicyChangeDto;
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    writes.push({ company, operation, body });
    if (failure) {
      const code = failure;
      failure = null;
      return route.fulfill({ status: code === "stale_version" ? 409 : 403, json: { code } });
    }
    const previous = receipts.get(`${company}:${operation}`);
    if (previous) {
      expect(JSON.stringify(body)).toBe(previous.payload);
      return route.fulfill({ json: { id: previous.id, version: previous.version } });
    }
    if ((records.get(company)?.current?.version ?? null) !== body.expectedVersion)
      return route.fulfill({ status: 409, json: { code: "stale_version" } });
    const receipt = commit(company, body);
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
    advance: (company: string) => {
      const current = records.get(company)?.current;
      if (!current) throw new Error("Missing fixture policy");
      commit(company, {
        ...current,
        minimumMonthlyWage: "4300000.75",
        expectedVersion: current.version,
        reason: "Independent policy update",
      });
    },
  };
}
