import { expect, type Page } from "@playwright/test";
import type { PayrollAmendmentDto } from "../src/features/payroll/data/models/payroll-amendment-dto";
import type { PayrollAmendmentReviewDetailsDto } from "../src/features/payroll/data/models/payroll-amendment-review-dto";
import { compensationActor } from "./fixtures/compensations";
import { amendmentReviewDetails, amendmentReviewId } from "./fixtures/payroll-amendment-reviews";
import {
  amendmentCalculation,
  amendmentId,
  amendmentRevision,
} from "./fixtures/payroll-amendments";
import { calculationCompany } from "./fixtures/payroll-calculations";
import { reviewFirst, reviewSecond } from "./fixtures/payroll-reviews";
import { installIdentityApi } from "./identity-api";

type Role = "author" | "first" | "second" | "publisher" | "reader";
function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installPayrollAmendmentReviewsApi(
  page: Page,
  options: { role?: Role; initial?: boolean; blocked?: boolean } = {},
) {
  let role = options.role ?? "author";
  const permissions = () => [
      role === "author"
        ? "payroll.amend"
        : role === "first" || role === "second"
          ? "payroll.amend.review"
          : role === "publisher"
            ? "payroll.amend.publish"
            : "payroll.read",
    ],
    actor = () =>
      role === "first" ? reviewFirst : role === "second" ? reviewSecond : compensationActor,
    identity = await installIdentityApi(page, {
      signedIn: true,
      mfa: true,
      mfaConfigured: true,
      mfaVerified: true,
      permissions: permissions(),
    });
  const revisions: PayrollAmendmentDto[] = [amendmentRevision(0), amendmentRevision(1)],
    calculation = amendmentCalculation(),
    records = new Map<string, PayrollAmendmentReviewDetailsDto>(),
    receipts = new Map<string, { id: string; version: number; path: string; body: string }>(),
    requests: {
      action: string;
      path: string;
      body: Record<string, unknown> | null;
      operation: string | null;
    }[] = [];
  let dropped: string | null = null,
    fail: { action: string; code: string; status: number } | null = null,
    held: {
      action: string;
      entered: ReturnType<typeof gate>;
      released: ReturnType<typeof gate>;
    } | null = null,
    denied = false;
  function current() {
    const value = revisions.at(-1);
    if (!value) throw new Error("Current amendment fixture required");
    return value;
  }
  function submit(id: string, reason: string) {
    const previous = current(),
      raw = amendmentReviewDetails(),
      at = new Date(Date.parse(previous.summary.recordedAt) + 1000).toISOString();
    raw.review = {
      ...raw.review,
      id,
      number: records.size + 1,
      amendmentVersion: previous.summary.version,
      approvalId: amendmentReviewId(100 + records.size),
      reason,
      submittedAt: at,
    };
    raw.approval.id = raw.review.approvalId;
    if (options.blocked) {
      raw.approval.status = "BLOCKED";
      raw.approval.stages[0] = [];
      raw.accounts = raw.accounts.filter((account) => account.id !== reviewFirst);
    }
    raw.history = [
      {
        revision: 0,
        amendmentRevision: previous.summary.version + 1,
        action: "SUBMITTED",
        status: "PENDING",
        approvalVersion: 0,
        step: null,
        actorId: compensationActor,
        decidingFor: null,
        reason,
        recordedAt: at,
      },
    ];
    const summary = {
      ...previous.summary,
      status: "IN_REVIEW" as const,
      action: "SUBMITTED" as const,
      version: previous.summary.version + 1,
      latestReviewId: id,
      reason,
      recordedAt: at,
    };
    revisions.push({ summary, terms: structuredClone(previous.terms) });
    raw.amendment = summary;
    records.set(id, raw);
    return raw;
  }
  function detail(raw: PayrollAmendmentReviewDetailsDto): PayrollAmendmentReviewDetailsDto {
    const actions: PayrollAmendmentReviewDetailsDto["availableActions"] = [],
      latest = current().summary;
    if (
      latest.latestReviewId === raw.review.id &&
      ["IN_REVIEW", "APPROVED"].includes(latest.status)
    ) {
      if (role === "author" && ["PENDING", "APPROVED"].includes(raw.review.status))
        actions.push("WITHDRAW");
      if (
        (role === "first" || role === "second") &&
        raw.review.status === "PENDING" &&
        raw.approval.status === "PENDING" &&
        raw.approval.stages[raw.approval.currentStep]?.includes(actor())
      )
        actions.push("APPROVE", "REJECT");
    }
    return {
      ...structuredClone(raw),
      amendment: structuredClone(latest),
      availableActions: actions,
    };
  }
  if (options.initial) submit(amendmentReviewId(), "Review the retained correction");
  await page.route("**/api/v1/companies/*/payroll/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      parts = url.pathname.split("/"),
      id = parts[7],
      action =
        parts[6] === "amendment-reviews"
          ? "review"
          : parts[8] === "reviews"
            ? request.method() === "PUT"
              ? "submit"
              : parts[10] === "decisions"
                ? "decide"
                : parts[10] === "withdraw"
                  ? "withdraw"
                  : "reviews"
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
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE amendment review diagnostic" },
      });
    if (parts[4] !== calculationCompany)
      return action === "directory"
        ? route.fulfill({ json: { items: [], nextCursor: null } })
        : problem("access_denied", 403);
    if (denied || role === "reader") return problem("access_denied", 403);
    if (fail?.action === action) {
      const failed = fail;
      fail = null;
      return problem(failed.code, failed.status);
    }
    if (action === "review") {
      const raw = records.get(id ?? "");
      return raw
        ? route.fulfill({ json: detail(raw) })
        : problem("payroll_amendment_review_not_found", 404);
    }
    if (action === "directory")
      return route.fulfill({ json: { items: [current().summary], nextCursor: null } });
    if (id !== amendmentId()) return problem("payroll_amendment_not_found", 404);
    if (action === "get")
      return route.fulfill({
        json: {
          amendment: current(),
          history: { items: revisions.map((row) => row.summary), nextCursor: null },
          latestCalculation: calculation.summary,
          latestReview: [...records.values()].at(-1)?.review ?? null,
          availableActions:
            role === "author" && current().summary.status === "CALCULATED"
              ? ["EDIT", "CANCEL", "SUBMIT"]
              : [],
        },
      });
    if (action === "revisions") return route.fulfill({ json: revisions[Number(parts[9])] });
    if (action === "history")
      return route.fulfill({
        json: {
          items: revisions
            .filter((row) => row.summary.version > Number(url.searchParams.get("after") ?? "-1"))
            .map((row) => row.summary),
          nextCursor: null,
        },
      });
    if (action === "calculations")
      return parts[9] === calculation.summary.id
        ? route.fulfill({ json: calculation })
        : problem("payroll_amendment_calculation_not_found", 404);
    if (action === "reviews")
      return route.fulfill({ json: [...records.values()].map((row) => row.review) });
    if (!body || !operation)
      throw new Error("Review command fixture requires its payload and operation");
    const reviewId = parts[9];
    if (!reviewId) throw new Error("Review command identity required");
    const original = receipts.get(operation);
    if (original) {
      expect(url.pathname).toBe(original.path);
      expect(JSON.stringify(body)).toBe(original.body);
      return route.fulfill({ json: { id: original.id, version: original.version } });
    }
    if (body.expectedVersion !== current().summary.version) return problem("stale_version");
    expect(typeof body.reason).toBe("string");
    expect(String(body.reason).trim().length).toBeGreaterThan(0);
    if (action === "submit") {
      if (role !== "author") return problem("access_denied", 403);
      if (current().summary.status !== "CALCULATED")
        return problem("payroll_amendment_review_active");
      expect(body.calculationId).toBe(calculation.summary.id);
      expect(Object.keys(body).sort()).toEqual(["calculationId", "expectedVersion", "reason"]);
      submit(reviewId, String(body.reason));
    } else if (action === "decide" || action === "withdraw") {
      const raw = records.get(reviewId);
      if (!raw) return problem("payroll_amendment_review_not_found", 404);
      if (
        body.expectedReviewVersion !== raw.review.version ||
        body.expectedApprovalVersion !== raw.approval.version
      )
        return problem("stale_version");
      const intended = action === "withdraw" ? "WITHDRAW" : body.decision;
      if (!detail(raw).availableActions.some((value) => value === intended))
        return problem("payroll_amendment_review_action_unavailable", 403);
      const previous = current(),
        reason = String(body.reason),
        at = new Date(Date.parse(previous.summary.recordedAt) + 1000).toISOString(),
        step = raw.approval.currentStep;
      raw.approval.version++;
      raw.review.version++;
      const change =
        action === "withdraw" ? "WITHDRAWN" : intended === "APPROVE" ? "APPROVED" : "REJECTED";
      if (action === "withdraw") {
        raw.review.status = "WITHDRAWN";
        raw.approval.status = "CANCELLED";
      } else if (intended === "REJECT") {
        raw.review.status = "REJECTED";
        raw.approval.status = "REJECTED";
      } else if (step + 1 === raw.approval.stages.length) {
        raw.review.status = "APPROVED";
        raw.approval.status = "APPROVED";
      } else raw.approval.currentStep++;
      raw.history.push({
        revision: raw.review.version,
        amendmentRevision: previous.summary.version + 1,
        action: change,
        status: raw.review.status,
        approvalVersion: raw.approval.version,
        step: action === "withdraw" ? null : step,
        actorId: actor(),
        decidingFor: action === "withdraw" ? null : actor(),
        reason,
        recordedAt: at,
      });
      revisions.push({
        summary: {
          ...previous.summary,
          version: previous.summary.version + 1,
          action: change,
          status:
            raw.review.status === "WITHDRAWN"
              ? "CALCULATED"
              : raw.review.status === "PENDING"
                ? "IN_REVIEW"
                : raw.review.status,
          actorId: actor(),
          reason,
          recordedAt: at,
        },
        terms: structuredClone(previous.terms),
      });
    } else throw new Error(`Unhandled amendment review fixture: ${action}`);
    const receipt = { id: amendmentId(), version: current().summary.version };
    receipts.set(operation, { ...receipt, path: url.pathname, body: JSON.stringify(body) });
    if (dropped === action) {
      dropped = null;
      return route.abort("failed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    identity,
    requests,
    records,
    receipts,
    revisions,
    count: (action: string) => requests.filter((row) => row.action === action).length,
    setRole(next: Role) {
      role = next;
      identity.setPermissions(permissions());
    },
    loseNext(action: string) {
      dropped = action;
    },
    failNext(action: string, code: string, status = 409) {
      fail = { action, code, status };
    },
    deny() {
      denied = true;
    },
    holdNext(action: string) {
      const waiting = { action, entered: gate(), released: gate() };
      held = waiting;
      return { entered: waiting.entered.promise, release: waiting.released.resolve };
    },
  };
}
