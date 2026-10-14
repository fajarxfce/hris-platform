import { expect, type Page } from "@playwright/test";
import type { PayrollFinalizationDetailsDto } from "../src/features/payroll/data/models/payroll-finalization-dto";
import {
  calculationActor,
  calculationCompany,
  calculationId,
  calculationRun,
} from "./fixtures/payroll-calculations";
import {
  finalizationId,
  payrollFinalizationDetails,
  payrollFinalizationPreparation,
} from "./fixtures/payroll-finalizations";
import { installPayrollCalculationsApi } from "./payroll-calculations-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

export async function installPayrollFinalizationsApi(
  page: Page,
  options: {
    permissions?: string[];
    initial?: PayrollFinalizationDetailsDto["job"]["status"];
    owned?: boolean;
    blocker?: string;
    runs?: number;
  } = {},
) {
  const permissions = options.permissions ?? ["payroll.finalize"],
    base = await installPayrollCalculationsApi(page, { permissions }),
    preparation = payrollFinalizationPreparation(),
    records = new Map<string, PayrollFinalizationDetailsDto>(),
    receipts = new Map<string, { id: string; version: number; payload: string }>(),
    reads: URL[] = [],
    writes: { path: string; operation: string; body: Record<string, unknown> }[] = [];
  const directory = options.runs
    ? Array.from({ length: options.runs }, (_, index) =>
        calculationRun({
          id: `ee900000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
          reviewReference: `Payroll reference ${index + 1}`,
        }),
      )
    : [preparation.run];
  let drop = false,
    failure: { code: string; status: number } | null = null,
    held: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null = null;
  const problem = (code: string) => ({
    code,
    fields: {},
    parameters: {},
    detail: "PRIVATE TECHNICAL DIAGNOSTIC",
  });
  function append(
    id: string,
    reason: string,
    status: PayrollFinalizationDetailsDto["job"]["status"],
  ) {
    const raw = payrollFinalizationDetails(status),
      number = records.size + 1,
      jobId = `ef100000-0000-4000-8000-${String(number).padStart(12, "0")}`;
    raw.finalization = {
      ...raw.finalization,
      id,
      reason,
      number,
      jobId,
      actorId: options.owned === false ? raw.finalization.actorId : calculationActor,
      approvalVersion: preparation.approvalVersion ?? 3,
    };
    raw.job.jobId = jobId;
    records.set(id, raw);
    if (status === "SUCCEEDED")
      preparation.run = { ...preparation.run, status: "FINALIZED", version: 2, finalizationId: id };
    return raw;
  }
  if (options.initial)
    append(finalizationId, "Publish independently approved payroll", options.initial);
  function currentPreparation() {
    const latest = [...records.values()].at(-1),
      code =
        options.blocker ??
        (preparation.run.status === "FINALIZED"
          ? "payroll_already_finalized"
          : latest && ["QUEUED", "RUNNING"].includes(latest.job.status)
            ? "payroll_finalization_active"
            : records.size >= 8
              ? "payroll_finalization_capacity"
              : null);
    return {
      ...preparation,
      latestFinalization: latest?.finalization ?? null,
      latestJobStatus: latest?.job.status ?? null,
      blockingIssues: code ? [{ kind: "CONFLICT", ...problem(code) }] : [],
      availableActions: !code && permissions.includes("payroll.finalize") ? ["START"] : [],
    };
  }
  await page.route("**/api/v1/companies/*/payroll/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      parts = url.pathname.split("/"),
      company = parts[4],
      section = parts[6],
      id = parts[7],
      action = parts[8];
    if (
      !(
        section === "publication-runs" ||
        section === "finalizations" ||
        (section === "runs" && ["finalizations", "finalization-preparation"].includes(action ?? ""))
      )
    )
      return route.fallback();
    expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
    if (request.method() === "GET") {
      reads.push(url);
      const waiting = held;
      if (waiting) {
        held = null;
        waiting.entered.resolve();
        await waiting.released.promise;
      }
      if (failure) {
        const next = failure;
        failure = null;
        return route.fulfill({ status: next.status, json: problem(next.code) });
      }
      if (company !== calculationCompany)
        return route.fulfill({
          status: section === "publication-runs" ? 200 : 404,
          json:
            section === "publication-runs"
              ? { items: [], nextCursor: null }
              : problem("payroll_finalization_not_found"),
        });
      if (section === "publication-runs") {
        expect(url.searchParams.get("limit")).toBe("50");
        const items = directory
          .map((row) => (row.id === calculationId ? preparation.run : row))
          .filter(
            (row) =>
              row.earningsMonth >= (url.searchParams.get("from") ?? "") &&
              row.earningsMonth <= (url.searchParams.get("until") ?? "") &&
              (!url.searchParams.has("status") || row.status === url.searchParams.get("status")) &&
              row.id > (url.searchParams.get("after") ?? ""),
          );
        return route.fulfill({
          json: { items: items.slice(0, 50), nextCursor: items.length > 50 ? items[49]?.id : null },
        });
      }
      if (section === "runs") {
        if (id !== calculationId)
          return route.fulfill({ status: 404, json: problem("payroll_run_not_found") });
        if (action === "finalization-preparation")
          return route.fulfill({ json: currentPreparation() });
        expect(url.searchParams.get("limit")).toBe("8");
        return route.fulfill({
          json: { items: [...records.values()].map((row) => row.finalization), nextCursor: null },
        });
      }
      const raw = id ? records.get(id) : null;
      return raw
        ? route.fulfill({ json: { ...raw, run: preparation.run } })
        : route.fulfill({ status: 404, json: problem("payroll_finalization_not_found") });
    }
    const operation = request.headers()["idempotency-key"] ?? "",
      body = request.postDataJSON() as Record<string, unknown>,
      payload = JSON.stringify(body);
    writes.push({ path: url.pathname, operation, body });
    expect(operation).not.toBe("");
    if (failure) {
      const next = failure;
      failure = null;
      return route.fulfill({ status: next.status, json: problem(next.code) });
    }
    const previous = receipts.get(operation);
    if (previous) {
      expect(previous.payload).toBe(payload);
      return route.fulfill({ json: { id: previous.id, version: previous.version } });
    }
    if (!permissions.includes("payroll.finalize"))
      return route.fulfill({ status: 403, json: problem("access_denied") });
    if (body.expectedApprovalVersion !== preparation.approvalVersion)
      return route.fulfill({ status: 409, json: problem("approval_changed") });
    expect(body.expectedRunVersion).toBe(preparation.run.version);
    expect(body.reviewId).toBe(preparation.latestReview?.id);
    expect(body.expectedReviewVersion).toBe(preparation.latestReview?.version);
    expect(currentPreparation().availableActions).toContain("START");
    const raw = append(String(body.id), String(body.reason), "QUEUED"),
      receipt = { id: raw.finalization.id, version: 0 };
    receipts.set(operation, { ...receipt, payload });
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    base,
    preparation,
    records,
    reads,
    writes,
    append,
    dropNext: () => {
      drop = true;
    },
    failNext: (code: string, status = 403) => {
      failure = { code, status };
    },
    holdNext: () => {
      const waiting = { entered: gate(), released: gate() };
      held = waiting;
      return waiting;
    },
    finish: (id: string, status: "FAILED" | "CANCELLED" | "SUCCEEDED") => {
      const raw = records.get(id);
      if (!raw) throw new Error("Publication fixture required");
      const next = payrollFinalizationDetails(status);
      raw.job = { ...next.job, jobId: raw.job.jobId };
      raw.finalization.publishedAt = next.finalization.publishedAt;
      if (status === "SUCCEEDED")
        preparation.run = {
          ...preparation.run,
          status: "FINALIZED",
          version: 2,
          finalizationId: id,
        };
    },
  };
}
