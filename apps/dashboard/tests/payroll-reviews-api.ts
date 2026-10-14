import { expect, type Page } from "@playwright/test";
import type { PayrollReviewDetailsDto } from "../src/features/payroll/data/models/payroll-review-dto";
import { calculationActor, calculationCompany } from "./fixtures/payroll-calculations";
import {
  payrollReviewDetails,
  payrollReviewPreparation,
  reviewFirst,
  reviewId,
  reviewSecond,
} from "./fixtures/payroll-reviews";
import { installPayrollCalculationsApi } from "./payroll-calculations-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installPayrollReviewsApi(
  page: Page,
  options: { role?: "preparer" | "reviewer" | "reader"; initial?: boolean; blocked?: boolean } = {},
) {
  const role = options.role ?? "preparer",
    permissions =
      role === "preparer"
        ? ["payroll.calculate"]
        : role === "reviewer"
          ? ["payroll.review"]
          : ["payroll.read"],
    base = await installPayrollCalculationsApi(page, { permissions });
  const records = new Map<string, PayrollReviewDetailsDto>(),
    receipts = new Map<string, { id: string; version: number; payload: string }>(),
    reads: URL[] = [],
    writes: { path: string; operation: string; body: Record<string, unknown> }[] = [];
  let drop = false,
    failure: { code: string; status: number } | null = null,
    held: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null = null;
  function newRecord() {
    const raw = payrollReviewDetails();
    if (role === "reviewer") {
      raw.run.actorId = reviewSecond;
      raw.review.submittedBy = reviewSecond;
      raw.approval.authorId = reviewSecond;
      raw.approval.excludedAccountIds = [reviewSecond];
      raw.approval.stages = [[calculationActor], [reviewFirst]];
      if (raw.changes[0]) raw.changes[0].actorId = reviewSecond;
      raw.accounts = [
        { id: reviewSecond, displayName: "Payroll Preparer" },
        { id: calculationActor, displayName: "Payroll Reviewer One" },
        { id: reviewFirst, displayName: "Payroll Reviewer Two" },
      ];
    }
    if (options.blocked) {
      raw.approval.status = "BLOCKED";
      raw.approval.stages[0] = [];
      raw.accounts = raw.accounts.filter(
        (account) =>
          account.id === raw.run.actorId || raw.approval.stages.flat().includes(account.id),
      );
    }
    return raw;
  }
  if (options.initial !== false) records.set(reviewId, newRecord());
  const problem = (code: string) => ({
    code,
    fields: {},
    parameters: {},
    detail: "PRIVATE TECHNICAL DIAGNOSTIC",
  });
  function detail(raw: PayrollReviewDetailsDto): PayrollReviewDetailsDto {
    const actions: PayrollReviewDetailsDto["availableActions"] = [];
    if (
      role === "preparer" &&
      ["PENDING", "APPROVED"].includes(raw.review.status) &&
      !raw.finalizationActive
    )
      actions.push("WITHDRAW");
    if (
      role === "reviewer" &&
      raw.review.status === "PENDING" &&
      raw.approval.status === "PENDING" &&
      raw.approval.stages[raw.approval.currentStep]?.includes(calculationActor)
    )
      actions.push("APPROVE", "REJECT");
    return { ...raw, availableActions: actions };
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
        section === "reviews" ||
        (section === "runs" && (action === "reviews" || action === "review-preparation"))
      )
    )
      return route.fallback();
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
        return route.fulfill({ status: 404, json: problem("payroll_review_not_found") });
      if (section === "runs") {
        if (action === "reviews")
          return route.fulfill({
            json: { items: [...records.values()].map((row) => row.review), nextCursor: null },
          });
        const preparation = payrollReviewPreparation(),
          latest = [...records.values()].at(-1)?.review ?? null;
        const code =
          latest?.status === "REJECTED"
            ? "payroll_recalculation_required"
            : latest && latest.status !== "WITHDRAWN"
              ? "payroll_review_active"
              : null;
        return route.fulfill({
          json: {
            ...preparation,
            latestReview: latest,
            blockingIssues: code ? [{ kind: "CONFLICT", ...problem(code) }] : [],
            availableActions: role === "preparer" && !code ? ["SUBMIT"] : [],
          },
        });
      }
      const raw = id ? records.get(id) : null;
      return raw
        ? route.fulfill({ json: detail(raw) })
        : route.fulfill({ status: 404, json: problem("payroll_review_not_found") });
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
    let receipt: { id: string; version: number };
    if (section === "runs") {
      if (role !== "preparer")
        return route.fulfill({ status: 403, json: problem("access_denied") });
      expect(body.expectedRunVersion).toBe(1);
      const raw = newRecord();
      raw.review.id = String(body.id);
      raw.review.reason = String(body.reason);
      raw.review.number = records.size + 1;
      raw.approval.id = raw.review.id;
      raw.review.approvalId = raw.review.id;
      if (raw.changes[0]) raw.changes[0].reason = String(body.reason);
      records.set(raw.review.id, raw);
      receipt = { id: raw.review.id, version: 0 };
    } else {
      const raw = id ? records.get(id) : null;
      if (!raw) return route.fulfill({ status: 404, json: problem("payroll_review_not_found") });
      const intended = action === "withdraw" ? "WITHDRAW" : body.decision;
      if (!detail(raw).availableActions.includes(intended as "APPROVE" | "REJECT" | "WITHDRAW"))
        return route.fulfill({ status: 403, json: problem("not_assigned_approver") });
      if (body.expectedVersion !== raw.review.version)
        return route.fulfill({ status: 409, json: problem("stale_version") });
      if (body.expectedApprovalVersion !== raw.approval.version)
        return route.fulfill({ status: 409, json: problem("approval_changed") });
      const step = raw.approval.currentStep;
      raw.review.version++;
      raw.approval.version++;
      if (action === "withdraw") {
        raw.review.status = "WITHDRAWN";
        raw.approval.status = "CANCELLED";
      } else if (body.decision === "REJECT") {
        raw.review.status = "REJECTED";
        raw.approval.status = "REJECTED";
      } else if (step + 1 === raw.approval.stages.length) {
        raw.review.status = "APPROVED";
        raw.approval.status = "APPROVED";
      } else raw.approval.currentStep++;
      raw.changes.push({
        revision: raw.review.version,
        action:
          action === "withdraw"
            ? "WITHDRAWN"
            : body.decision === "REJECT"
              ? "REJECTED"
              : "APPROVED",
        status: raw.review.status,
        approvalVersion: raw.approval.version,
        step: action === "withdraw" ? null : step,
        actorId: calculationActor,
        decidingFor: action === "withdraw" ? null : calculationActor,
        reason: String(body.reason),
        at: "2026-10-01T00:05:00Z",
      });
      receipt = { id: raw.review.id, version: raw.review.version };
    }
    receipts.set(operation, { ...receipt, payload });
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    base,
    records,
    reads,
    writes,
    dropNext: () => {
      drop = true;
    },
    failNext: (code: string, status = 403) => {
      failure = { code, status };
    },
    reassign: () => {
      const raw = records.get(reviewId);
      if (raw) raw.approval.version++;
    },
    holdNext: () => {
      const waiting = { entered: gate(), released: gate() };
      held = waiting;
      return waiting;
    },
  };
}
