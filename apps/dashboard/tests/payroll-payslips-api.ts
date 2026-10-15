import { expect, type Page } from "@playwright/test";
import { calculationCompany } from "./fixtures/payroll-calculations";
import {
  payslipDetails,
  payslipId,
  payslipPayments,
  payslipPdf,
  payslipSummary,
} from "./fixtures/payroll-payslips";
import { installIdentityApi } from "./identity-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installPayrollPayslipsApi(
  page: Page,
  options: { permissions?: readonly string[]; count?: number; currentRevision?: number } = {},
) {
  const permissions = options.permissions ?? ["payroll.read"];
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  let currentRevision = options.currentRevision ?? 0;
  const rows = Array.from({ length: options.count ?? 3 }, (_, i) =>
    payslipSummary(i + 1, { version: currentRevision }),
  );
  const reads: URL[] = [],
    payments = payslipPayments();
  payments.version = Math.max(payments.version, currentRevision);
  payments.settlement.assessmentRevision = currentRevision;
  let failure: { action: string; code: string; status: number } | null = null;
  let held: {
    action: string;
    entered: ReturnType<typeof gate>;
    released: ReturnType<typeof gate>;
  } | null = null;
  let denied = false;
  await page.route("**/api/v1/companies/*/payroll/payslips**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      parts = url.pathname.split("/"),
      id = parts[7],
      action = parts[8] ?? (id ? "get" : "list");
    reads.push(url);
    expect(request.method()).toBe("GET");
    expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
    expect(["AUTHORIZED", "SELF"]).toContain(url.searchParams.get("scope"));
    const waiting = held;
    if (waiting?.action === action) {
      held = null;
      waiting.entered.resolve();
      await waiting.released.promise;
    }
    const problem = (code: string, status: number) =>
      route.fulfill({
        status,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE PAYROLL DIAGNOSTIC" },
      });
    if (
      denied ||
      !permissions.some((value) => ["payroll.read", "payroll.self.read"].includes(value))
    )
      return problem("access_denied", 403);
    if (failure?.action === action) {
      const value = failure;
      failure = null;
      return problem(value.code, value.status);
    }
    const self = url.searchParams.get("scope") === "SELF" || !permissions.includes("payroll.read");
    const scoped = parts[4] !== calculationCompany ? [] : self ? rows.slice(0, 1) : rows;
    if (!id) {
      expect(url.searchParams.get("limit")).toBe("50");
      const from = url.searchParams.get("from") ?? "",
        until = url.searchParams.get("until") ?? "",
        query = (url.searchParams.get("query") ?? "").toLowerCase(),
        after = url.searchParams.get("after");
      const matching = scoped.filter(
        (row) =>
          row.taxMonth >= from &&
          row.taxMonth <= until &&
          (!after || row.id > after.slice(8)) &&
          `${row.employeeName}\n${row.employeeNumber}`.toLowerCase().includes(query),
      );
      const items = matching.slice(0, 50),
        last = items.at(-1);
      return route.fulfill({
        json: {
          items,
          nextCursor: matching.length > 50 && last ? `${last.taxMonth}:${last.id}` : null,
        },
      });
    }
    const row = scoped.find((row) => row.id === id);
    if (!row) return problem("payroll_payslip_not_found", 404);
    const index = rows.indexOf(row) + 1;
    if (action === "payments") return route.fulfill({ json: { ...payments, assessmentId: id } });
    const selected = url.searchParams.get("revision"),
      revision = selected === null ? currentRevision : Number(selected);
    if (!Number.isSafeInteger(revision) || revision < 0 || revision > currentRevision)
      return problem("payroll_payslip_not_found", 404);
    if (action === "pdf") {
      expect(request.headers().accept).toBe("application/pdf");
      const pdf = payslipPdf();
      return route.fulfill({
        body: Buffer.from(pdf.parts[0]),
        headers: {
          "Content-Type": "application/pdf",
          "Content-Length": String(pdf.byteLength),
          ETag: pdf.etag,
        },
      });
    }
    const detail = payslipDetails(index);
    detail.summary.version = revision;
    return route.fulfill({ json: detail });
  });
  return {
    identity,
    reads,
    payments,
    publishRevision(revision: number) {
      currentRevision = revision;
      for (const row of rows) row.version = revision;
      payments.version = Math.max(payments.version + 1, revision);
      payments.settlement.assessmentRevision = revision;
    },
    failNext(action: string, code: string, status = 503) {
      failure = { action, code, status };
    },
    holdNext(action: string) {
      const waiting = { action, entered: gate(), released: gate() };
      held = waiting;
      return { entered: waiting.entered.promise, release: waiting.released.resolve };
    },
    deny() {
      denied = true;
      identity.setPermissions(["company.read"]);
    },
    count(action: string) {
      return reads.filter(
        (url) =>
          (url.pathname.split("/")[8] ?? (url.pathname.split("/")[7] ? "get" : "list")) === action,
      ).length;
    },
    ownId: payslipId(),
  };
}
