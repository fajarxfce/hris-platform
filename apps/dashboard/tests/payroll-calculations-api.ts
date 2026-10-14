import { expect, type Page } from "@playwright/test";
import type {
  PayrollRunDetailsDto,
  PayrollRunDto,
} from "../src/features/payroll/data/models/payroll-calculation-dto";
import {
  calculationActor,
  calculationDetails,
  calculationEmployee,
  calculationId,
  calculationItem,
  calculationPeriod,
  calculationReview,
  calculationRun,
} from "./fixtures/payroll-calculations";
import { periodDetails } from "./fixtures/payroll-periods";
import { companyIds, installIdentityApi } from "./identity-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installPayrollCalculationsApi(
  page: Page,
  options: {
    permissions?: string[];
    initial?: "draft" | "calculated" | "processing" | "stopped";
    employees?: number;
    failed?: number;
  } = {},
) {
  const permissions = options.permissions ?? ["payroll.calculate"],
    identity = await installIdentityApi(page, {
      signedIn: true,
      mfa: true,
      mfaConfigured: true,
      mfaVerified: true,
      permissions,
    });
  const review = calculationReview(),
    records = new Map<string, PayrollRunDetailsDto>(),
    receipts = new Map<string, { id: string; version: number; payload: string }>();
  const reads: URL[] = [],
    writes: { path: string; operation: string; body: Record<string, unknown> }[] = [];
  let drop = false,
    failure: { code: string; status: number } | null = null,
    held: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null = null;
  review.period.participantCount = options.employees ?? 1;
  if ((options.initial ?? "calculated") !== "draft") {
    const status =
        options.initial === "processing"
          ? "PROCESSING"
          : options.initial === "stopped"
            ? "STOPPED"
            : "CALCULATED",
      processed = status === "CALCULATED" ? review.period.participantCount : 0,
      failed = options.failed ?? 0;
    const run = calculationRun({
        status,
        version: status === "PROCESSING" ? 0 : 1,
        totalEmployees: review.period.participantCount,
        processed,
        succeeded: processed - failed,
        failed,
      }),
      details = calculationDetails(run, 0, 5000);
    records.set(run.id, details);
    review.period = { ...details.period };
  }
  const problem = (code: string) => ({
    code,
    fields: {},
    parameters: {},
    detail: "PRIVATE TECHNICAL DIAGNOSTIC",
  });
  function actions(detail: PayrollRunDetailsDto) {
    return permissions.includes("payroll.calculate") ? detail.availableActions : [];
  }
  function currentReview() {
    return {
      ...review,
      runCount: records.size,
      availableActions:
        permissions.includes("payroll.calculate") && review.period.status === "DRAFT"
          ? ["START"]
          : [],
      blockingIssues:
        review.period.status === "DRAFT"
          ? []
          : [{ kind: "CONFLICT", code: "payroll_period_not_draft", fields: {}, parameters: {} }],
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
      if (company !== companyIds[0])
        return route.fulfill({
          json:
            section === "periods" && !id
              ? { items: [], nextCursor: null }
              : problem("payroll_period_not_found"),
          status: section === "periods" && !id ? 200 : 404,
        });
      if (section === "periods") {
        if (!id) return route.fulfill({ json: { items: [review.period], nextCursor: null } });
        if (id !== calculationPeriod)
          return route.fulfill({ status: 404, json: problem("payroll_period_not_found") });
        if (action === "calculation-review") return route.fulfill({ json: currentReview() });
        if (action === "runs")
          return route.fulfill({
            json: { items: [...records.values()].map((row) => row.run), nextCursor: null },
          });
        return route.fulfill({ json: periodDetails(review.period) });
      }
      const detail = id ? records.get(id) : null;
      if (!detail) return route.fulfill({ status: 404, json: problem("payroll_run_not_found") });
      if (action === "employees") {
        const item = detail.results.items.find((row) => row.target.employeeId === parts[9]);
        if (!item)
          return route.fulfill({ status: 404, json: problem("payroll_run_result_not_found") });
        return route.fulfill({
          json: { ...calculationEmployee(item.failure?.code ?? null), item },
        });
      }
      const after = Number(url.searchParams.get("after") ?? 0),
        limit = Number(url.searchParams.get("limit") ?? 50),
        items = detail.results.items.filter((row) => row.target.ordinal > after);
      return route.fulfill({
        json: {
          ...detail,
          period: review.period,
          availableActions: actions(detail),
          results: {
            items: items.slice(0, limit),
            nextCursor: items.length > limit ? String(items[limit - 1]?.target.ordinal) : null,
          },
        },
      });
    }
    const body = request.postDataJSON() as Record<string, unknown>,
      operation = request.headers()["idempotency-key"] ?? "",
      payload = JSON.stringify({ path: url.pathname, body });
    writes.push({ path: url.pathname, operation, body });
    expect(operation).not.toBe("");
    if (failure) {
      const next = failure;
      failure = null;
      return route.fulfill({ status: next.status, json: problem(next.code) });
    }
    const old = receipts.get(operation);
    if (old) {
      expect(old.payload).toBe(payload);
      return route.fulfill({ json: { id: old.id, version: old.version } });
    }
    let receipt: { id: string; version: number };
    if (section === "periods" && action === "runs") {
      if (
        body.expectedPeriodVersion !== review.period.version ||
        body.expectedPolicyVersion !== review.policy?.version
      )
        return route.fulfill({ status: 409, json: problem("stale_policy_version") });
      const run = calculationRun({
        id: String(body.id),
        number: records.size + 1,
        actorId: calculationActor,
        incomeDueDate: String(body.incomeDueDate),
        reviewReference: String(body.reviewReference),
        reason: String(body.reason),
        status: "PROCESSING",
        version: 0,
        totalEmployees: review.period.participantCount,
        processed: 0,
        succeeded: 0,
        failed: 0,
      });
      const detail = calculationDetails(run);
      review.period = {
        ...review.period,
        status: "PROCESSING",
        version: review.period.version + 1,
        currentRunId: run.id,
      };
      records.set(run.id, detail);
      receipt = { id: run.id, version: 0 };
    } else {
      const detail = id ? records.get(id) : null;
      if (!detail) return route.fulfill({ status: 404, json: problem("payroll_run_not_found") });
      if (
        body.expectedVersion !== detail.run.version ||
        (action === "abandon" && body.expectedPeriodVersion !== review.period.version)
      )
        return route.fulfill({ status: 409, json: problem("stale_version") });
      detail.run = {
        ...detail.run,
        version: detail.run.version + 1,
        status: action === "resume" ? "PROCESSING" : "ABANDONED",
      };
      if (action === "resume") {
        const job = `ec700000-0000-4000-8000-${String(detail.attempts.length + 1).padStart(12, "0")}`;
        detail.run.jobId = job;
        detail.attempts.push({
          jobId: job,
          number: detail.attempts.length + 1,
          baseCompleted: detail.run.processed,
          startedAt: "2026-10-01T00:02:00Z",
          reason: String(body.reason),
        });
        detail.job = {
          ...detail.job,
          jobId: job,
          status: "QUEUED",
          completedItems: 0,
          totalItems: detail.run.totalEmployees - detail.run.processed + 1,
          version: 0,
          cancellationRequested: false,
        };
        detail.availableActions = ["CANCEL_JOB"];
      } else {
        review.period = {
          ...review.period,
          status: "DRAFT",
          version: review.period.version + 1,
          currentRunId: null,
        };
        detail.availableActions = [];
      }
      receipt = { id: detail.run.id, version: detail.run.version };
    }
    receipts.set(operation, { ...receipt, payload });
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
    records,
    review,
    dropNext: () => {
      drop = true;
    },
    failNext: (code: string, status = 403) => {
      failure = { code, status };
    },
    holdNext: () => {
      const next = { entered: gate(), released: gate() };
      held = next;
      return next;
    },
    changePolicy: () => {
      if (review.policy)
        review.policy = {
          ...review.policy,
          version: review.policy.version + 1,
          appliedRevision: review.policy.appliedRevision + 1,
        };
    },
    complete: (id = calculationId, failed = 0) => {
      const detail = records.get(id);
      if (!detail) throw new Error("Run required");
      const base = detail.attempts.at(-1)?.baseCompleted ?? 0;
      detail.run = {
        ...detail.run,
        status: "CALCULATED",
        version: detail.run.version + 1,
        processed: detail.run.totalEmployees,
        succeeded: detail.run.totalEmployees - failed,
        failed,
      };
      detail.results = {
        items: Array.from({ length: detail.run.totalEmployees }, (_, i) =>
          i < base
            ? (detail.results.items[i] ?? calculationItem(i + 1))
            : {
                ...calculationItem(
                  i + 1,
                  i < failed ? "payroll_input_verification_required" : null,
                ),
                jobId: detail.job.jobId,
              },
        ),
        nextCursor: null,
      };
      detail.job = { ...detail.job, status: "SUCCEEDED", completedItems: detail.job.totalItems };
      detail.availableActions = ["ABANDON"];
      review.period = {
        ...review.period,
        status: "CALCULATED",
        version: review.period.version + 1,
      };
    },
    replaceRun: (run: PayrollRunDto) => {
      const detail = calculationDetails(run, 0, 5000);
      records.set(run.id, detail);
      review.period = { ...detail.period };
    },
  };
}
