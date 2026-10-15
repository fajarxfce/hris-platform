import { expect, type Page } from "@playwright/test";
import type { PayrollAmendmentDto } from "../src/features/payroll/data/models/payroll-amendment-dto";
import type { PayrollAmendmentPublicationDetailsDto } from "../src/features/payroll/data/models/payroll-amendment-publication-dto";
import {
  amendmentPublicationDetails,
  amendmentPublicationId,
  approvedAmendmentDetails,
  approvedAmendmentReview,
} from "./fixtures/payroll-amendment-publications";
import { amendmentCalculation, amendmentId } from "./fixtures/payroll-amendments";
import { calculationCompany } from "./fixtures/payroll-calculations";
import { installIdentityApi } from "./identity-api";

type Role = "publisher" | "author" | "reviewer" | "reader";
type Outcome = "PENDING" | "PUBLISHED" | "STOPPED" | "EXHAUSTED";
function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installPayrollAmendmentPublicationsApi(
  page: Page,
  options: { role?: Role; initial?: Outcome; count?: number } = {},
) {
  const role = options.role ?? "publisher",
    permissions = [
      role === "publisher"
        ? "payroll.amend.publish"
        : role === "author"
          ? "payroll.amend"
          : role === "reviewer"
            ? "payroll.amend.review"
            : "payroll.read",
    ],
    identity = await installIdentityApi(page, {
      signedIn: true,
      mfa: true,
      mfaConfigured: true,
      mfaVerified: true,
      permissions,
    }),
    approved = approvedAmendmentDetails(),
    review = approvedAmendmentReview(),
    revisions: PayrollAmendmentDto[] = approved.history.items.map((summary) => ({
      summary: structuredClone(summary),
      terms: structuredClone(approved.amendment.terms),
    })),
    attempts: PayrollAmendmentPublicationDetailsDto[] = [],
    receipts = new Map<string, { id: string; version: number; body: string; path: string }>(),
    requests: {
      action: string;
      path: string;
      body: Record<string, unknown> | null;
      operation: string | null;
    }[] = [];
  let lost: string | null = null,
    failure: { action: string; code: string; status: number } | null = null,
    denied = false,
    held: {
      action: string;
      entered: ReturnType<typeof gate>;
      released: ReturnType<typeof gate>;
    } | null = null;
  function current() {
    const latest = revisions.at(-1);
    if (!latest) throw new Error("Current amendment required");
    return latest;
  }
  function advance(
    status: PayrollAmendmentDto["summary"]["status"],
    action: PayrollAmendmentDto["summary"]["action"],
    publication: string,
    reason: string,
  ) {
    const previous = current();
    revisions.push({
      ...structuredClone(previous),
      summary: {
        ...previous.summary,
        version: previous.summary.version + 1,
        status,
        action,
        latestPublicationId: publication,
        actorId: "20000000-0000-4000-8000-000000000001",
        reason,
        recordedAt: new Date(Date.parse(previous.summary.recordedAt) + 1000).toISOString(),
      },
    });
  }
  function complete(outcome: "PUBLISHED" | "STOPPED" | "EXHAUSTED") {
    const row = attempts.at(-1);
    if (!row) throw new Error("Publication required");
    row.job = {
      ...row.job,
      status: outcome === "PUBLISHED" ? "SUCCEEDED" : "FAILED",
      attempts: 1,
      version: row.job.version + 2,
      completedItems: outcome === "PUBLISHED" ? 1 : 0,
      failureCode: outcome === "PUBLISHED" ? null : "job_failed",
    };
    if (outcome !== "EXHAUSTED") {
      advance(
        outcome === "PUBLISHED" ? "PUBLISHED" : "APPROVED",
        outcome === "PUBLISHED" ? "PUBLISHED" : "PUBLICATION_STOPPED",
        row.publication.id,
        row.publication.reason,
      );
      row.publication = {
        ...row.publication,
        status: outcome,
        completedAt: current().summary.recordedAt,
        completedBy: row.publication.actorId,
        failureCode: outcome === "PUBLISHED" ? null : "job_failed",
      };
    }
  }
  function start(id: string, reason: string) {
    const previous = current(),
      row = amendmentPublicationDetails(),
      number = attempts.length + 1;
    advance("PUBLISHING", "PUBLICATION_STARTED", id, reason);
    const jobId = `fc200000-0000-4000-8000-${String(number).padStart(12, "0")}`;
    row.publication = {
      ...row.publication,
      id,
      number,
      amendmentVersion: previous.summary.version,
      actorId: current().summary.actorId,
      requestedAt: current().summary.recordedAt,
      jobId,
      reason,
    };
    row.job.jobId = jobId;
    row.amendment = structuredClone(current().summary);
    attempts.push(row);
  }
  for (let i = 0; i < (options.count ?? (options.initial ? 1 : 0)); i++) {
    start(amendmentPublicationId(i + 1), "Publish reviewed correction");
    const outcome = options.count ? "STOPPED" : options.initial;
    if (outcome && outcome !== "PENDING") complete(outcome);
  }
  await page.route("**/api/v1/companies/*/payroll/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      parts = url.pathname.split("/"),
      id = parts[7],
      action =
        parts[6] === "amendment-reviews"
          ? "review"
          : parts[8] === "publications"
            ? request.method() === "PUT"
              ? "start"
              : request.method() === "POST"
                ? "recover"
                : "publications"
            : (parts[8] ?? (id ? "get" : "directory")),
      body =
        request.method() === "GET" ? null : (request.postDataJSON() as Record<string, unknown>),
      operation = request.headers()["idempotency-key"] ?? null;
    requests.push({ action, path: url.pathname, body, operation });
    const waiting = held;
    if (waiting?.action === action) {
      held = null;
      waiting.entered.resolve();
      await waiting.released.promise;
    }
    const problem = (code: string, status = 409) =>
      route.fulfill({
        status,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE publication diagnostic" },
      });
    if (parts[4] !== calculationCompany)
      return action === "directory"
        ? route.fulfill({ json: { items: [], nextCursor: null } })
        : problem("access_denied", 403);
    if (denied || role === "reader") return problem("access_denied", 403);
    if (failure?.action === action) {
      const result = failure;
      failure = null;
      return problem(result.code, result.status);
    }
    if (action === "review")
      return id === review.review.id
        ? route.fulfill({ json: { ...review, amendment: current().summary, availableActions: [] } })
        : problem("payroll_amendment_review_not_found", 404);
    if (action === "directory")
      return route.fulfill({ json: { items: [current().summary], nextCursor: null } });
    if (id !== amendmentId()) return problem("payroll_amendment_not_found", 404);
    if (action === "get")
      return route.fulfill({
        json: {
          ...approved,
          amendment: current(),
          history: { items: revisions.map((row) => row.summary), nextCursor: null },
          availableActions: [],
        },
      });
    if (action === "history")
      return route.fulfill({
        json: {
          items: revisions
            .filter((row) => row.summary.version > Number(url.searchParams.get("after") ?? "-1"))
            .map((row) => row.summary),
          nextCursor: null,
        },
      });
    if (action === "revisions")
      return revisions[Number(parts[9])]
        ? route.fulfill({ json: revisions[Number(parts[9])] })
        : problem("payroll_amendment_not_found", 404);
    if (action === "calculations") return route.fulfill({ json: amendmentCalculation() });
    if (action === "publications")
      return route.fulfill({
        json: attempts.map((row, index) => ({
          ...row,
          amendment: current().summary,
          availableActions:
            role === "publisher" &&
            index === attempts.length - 1 &&
            row.publication.status === "PENDING" &&
            ["FAILED", "CANCELLED"].includes(row.job.status)
              ? ["RECOVER"]
              : [],
        })),
      });
    if (!["start", "recover"].includes(action) || !body || !operation)
      throw new Error(`Unhandled publication fixture ${request.method()} ${url.pathname}`);
    if (role !== "publisher") return problem("access_denied", 403);
    const previous = receipts.get(operation),
      encoded = JSON.stringify(body);
    if (previous) {
      expect(previous.body).toBe(encoded);
      expect(previous.path).toBe(url.pathname);
      return route.fulfill({ json: { id: previous.id, version: previous.version } });
    }
    if (body.expectedVersion !== current().summary.version) return problem("stale_version");
    if (action === "start") {
      if (current().summary.status !== "APPROVED")
        return problem("payroll_amendment_approved_review_required");
      if (attempts.length >= 8) return problem("payroll_amendment_publication_limit");
      expect(body.reviewId).toBe(review.review.id);
      expect(body.expectedReviewVersion).toBe(1);
      expect(body.expectedApprovalVersion).toBe(1);
      expect(body.calculationId).toBe(review.calculation.id);
      start(parts[9] ?? "", String(body.reason));
    } else {
      const last = attempts.at(-1);
      if (
        !last ||
        last.publication.id !== parts[9] ||
        last.publication.status !== "PENDING" ||
        last.job.status !== "FAILED"
      )
        return problem("payroll_amendment_recovery_unavailable");
      if (last.job.version !== body.expectedJobVersion) return problem("stale_version");
      advance("APPROVED", "PUBLICATION_STOPPED", last.publication.id, String(body.reason));
      last.publication = {
        ...last.publication,
        status: "STOPPED",
        failureCode: "job_failed",
        completedAt: current().summary.recordedAt,
        completedBy: current().summary.actorId,
      };
    }
    const receipt = { id: amendmentId(), version: current().summary.version };
    receipts.set(operation, { ...receipt, path: url.pathname, body: encoded });
    if (lost === action) {
      lost = null;
      return route.abort("failed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    identity,
    requests,
    attempts,
    receipts,
    complete,
    count: (action: string) => requests.filter((row) => row.action === action).length,
    loseNext: (action: string) => {
      lost = action;
    },
    failNext: (action: string, code: string, status = 409) => {
      failure = { action, code, status };
    },
    deny: () => {
      denied = true;
    },
    holdNext: (action: string) => {
      const value = { action, entered: gate(), released: gate() };
      held = value;
      return { entered: value.entered.promise, release: value.released.resolve };
    },
  };
}
