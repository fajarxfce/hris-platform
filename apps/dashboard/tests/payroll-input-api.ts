import { expect, type Page } from "@playwright/test";
import type {
  PayrollInputChangeDto,
  PayrollInputDto,
} from "../src/features/payroll/data/models/payroll-input-dto";
import {
  inputActor,
  inputAdjustment,
  inputEmployee,
  inputId,
  inputJob,
  inputRecord,
  inputReference,
  inputReview,
  inputReviewer,
} from "./fixtures/payroll-inputs";
import { companyIds, installIdentityApi } from "./identity-api";
export async function installPayrollInputApi(
  page: Page,
  permissions = ["payroll.calculate"],
  empty = false,
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const revisions: PayrollInputDto[] = empty
    ? []
    : Array.from({ length: 3 }, (_, i) => inputRecord(i));
  const reads: URL[] = [],
    writes: { operation: string; method: string; body: Record<string, unknown> }[] = [];
  const receipts = new Map<string, { id: string; version: number; payload: string }>();
  let failure: string | null = null,
    drop = false,
    sourceVersion = 2;
  let adjustment: ReturnType<typeof inputAdjustment> | null = null;
  await page.route("**/api/v1/companies/*/payroll/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      company = url.pathname.split("/")[4] ?? "",
      employee = url.pathname.split("/")[7],
      month = url.pathname.split("/")[9] ?? "2026-09",
      current = revisions.at(-1) ?? null;
    const problem = (code: string, status = 409) =>
      route.fulfill({ status, json: { code, fields: {}, parameters: {} } });
    expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
    if (request.method() === "GET") {
      reads.push(url);
      if (failure) {
        const code = failure;
        failure = null;
        return problem(code, 403);
      }
      if (url.pathname.endsWith("/inputs/employees")) {
        const query = url.searchParams.get("query")?.toLowerCase() ?? "",
          status = url.searchParams.get("status");
        const rows =
          company === companyIds[0]
            ? [
                {
                  ...inputReference(),
                  inputVersion: current?.version ?? null,
                  inputStatus: current?.status ?? null,
                },
              ]
            : [
                {
                  ...inputReference(),
                  id: "ba100000-0000-4000-8000-000000000002",
                  name: "South Payroll Employee",
                  inputVersion: null,
                  inputStatus: null,
                },
              ];
        return route.fulfill({
          json: {
            items: rows.filter(
              (r) =>
                (r.name.toLowerCase().includes(query) ||
                  r.employeeNumber.toLowerCase().includes(query)) &&
                (!status || (r.inputStatus ?? "UNPREPARED") === status),
            ),
            nextCursor: null,
          },
        });
      }
      if (employee !== inputEmployee || company !== companyIds[0])
        return problem("employee_not_found", 404);
      if (url.pathname.endsWith("/review")) {
        const after = url.searchParams.get("historyAfter"),
          raw = inputReview(current, after);
        raw.earningsMonth = month;
        expect(raw.source).not.toBeNull();
        if (raw.source) {
          raw.source.earningsMonth = month;
          raw.source.version = sourceVersion;
          raw.source.adjustment = adjustment;
        }
        raw.history = {
          items: revisions.filter((r) => after === null || r.version > Number(after)).slice(0, 20),
          nextCursor: null,
        };
        raw.availableActions = permissions.includes("payroll.calculate")
          ? ["PREPARE"]
          : permissions.includes("payroll.review") &&
              current?.status === "DRAFT" &&
              current.workPeriodVersion === sourceVersion &&
              current.workAdjustmentId === (adjustment?.id ?? null)
            ? ["VERIFY"]
            : [];
        return route.fulfill({ json: raw });
      }
      const revision = revisions.find(
        (r) => r.version === Number(url.searchParams.get("revision")),
      );
      return revision ? route.fulfill({ json: revision }) : problem("payroll_input_not_found", 404);
    }
    const body = request.postDataJSON() as Record<string, unknown>,
      operation = request.headers()["idempotency-key"] ?? "";
    writes.push({ operation, method: request.method(), body });
    if (failure) {
      const code = failure;
      failure = null;
      return problem(code);
    }
    const replay = receipts.get(operation);
    if (replay) {
      expect(replay.payload).toBe(JSON.stringify(body));
      return route.fulfill({ json: { id: replay.id, version: replay.version } });
    }
    if (body.expectedVersion !== (current?.version ?? null)) return problem("stale_version");
    let next: PayrollInputDto;
    if (request.method() === "PUT") {
      const command = body as unknown as PayrollInputChangeDto;
      if (
        command.expectedWorkPeriodVersion !== sourceVersion ||
        (command.workAdjustmentId ?? null) !== (adjustment?.id ?? null)
      )
        return problem("stale_workforce_version");
      next = {
        ...inputRecord((current?.version ?? -1) + 1),
        earningsMonth: month,
        terms: command.terms,
        reason: command.reason,
        workJobId: inputJob,
        workPeriodVersion: sourceVersion,
        workAdjustmentId: command.workAdjustmentId ?? null,
        employmentVersion: command.expectedEmploymentVersion,
        preparedBy: inputActor,
      };
    } else {
      if (!current) return problem("payroll_input_not_found", 404);
      if (current.workAdjustmentId !== (adjustment?.id ?? null))
        return problem("stale_workforce_version");
      next = {
        ...current,
        version: current.version + 1,
        status: "VERIFIED",
        verifiedBy: inputReviewer,
        reason: String(body.reason),
      };
    }
    revisions.push(next);
    const receipt = { id: inputId, version: next.version };
    receipts.set(operation, { ...receipt, payload: JSON.stringify(body) });
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    identity,
    reads,
    writes,
    revisions,
    dropNext: () => {
      drop = true;
    },
    failNext: (code: string) => {
      failure = code;
    },
    advanceSource: () => {
      sourceVersion++;
    },
    adjustAttendance: () => {
      adjustment = inputAdjustment((adjustment?.revision ?? 0) + 1);
      return adjustment;
    },
  };
}
