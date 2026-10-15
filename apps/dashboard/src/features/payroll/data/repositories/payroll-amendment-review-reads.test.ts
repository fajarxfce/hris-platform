import { describe, expect, it, vi } from "vitest";
import {
  amendmentReview,
  amendmentReviewDetails,
  amendmentReviewId,
} from "../../../../../tests/fixtures/payroll-amendment-reviews";
import { amendmentId } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import { reviewFirst, reviewSecond } from "../../../../../tests/fixtures/payroll-reviews";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { HttpPayrollAmendmentReviewDataSource } from "../datasources/http-payroll-amendment-review-data-source";
import type { PayrollAmendmentReviewDetailsDto } from "../models/payroll-amendment-review-dto";
import { RemotePayrollAmendmentReviewRepository } from "./remote-payroll-amendment-review-repository";

const company = calculationCompany as CompanyId,
  signal = new AbortController().signal;
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return {
    http,
    repository: new RemotePayrollAmendmentReviewRepository(
      new HttpPayrollAmendmentReviewDataSource(http),
    ),
  };
}
function firstChange(raw: PayrollAmendmentReviewDetailsDto) {
  const change = raw.history[0];
  if (!change) throw new Error("Submitted review change required");
  return change;
}
describe("retained amendment review transport", () => {
  it("acquires the scoped review and freezes approval, history and current references", async () => {
    const raw = amendmentReviewDetails(),
      f = fixture(raw);
    const result = await f.repository.get(company, amendmentReviewId(), signal);
    expect(result.ok).toBe(true);
    if (!result.ok) throw new Error("Expected review");
    expect(result.value.review.amendmentVersion).toBe(1);
    expect(result.value.amendment.version).toBe(2);
    expect(Object.isFrozen(result.value.approval.stages[0])).toBe(true);
    expect(Object.isFrozen(result.value.history[0])).toBe(true);
    expect(Object.isFrozen(result.value.accounts[0])).toBe(true);
    raw.approval.stages[0]?.push(amendmentId(5));
    expect(result.value.approval.stages[0]).toHaveLength(1);
    expect(f.http.request).toHaveBeenCalledExactlyOnceWith(
      { path: `/api/v1/companies/${company}/payroll/amendment-reviews/${amendmentReviewId()}` },
      signal,
    );
  });
  it.each<[(raw: PayrollAmendmentReviewDetailsDto) => void]>([
    [
      (raw) => {
        raw.review.id = amendmentReviewId(2);
      },
    ],
    [
      (raw) => {
        raw.review.amendmentId = amendmentId(2);
      },
    ],
    [
      (raw) => {
        raw.review.calculationId = amendmentReviewId(4);
      },
    ],
    [
      (raw) => {
        raw.review.amendmentVersion = 2;
      },
    ],
    [
      (raw) => {
        raw.review.timezone = "private/invalid";
      },
    ],
    [
      (raw) => {
        raw.approval.authorId = amendmentId(2);
      },
    ],
    [
      (raw) => {
        raw.approval.currentStep = 7;
      },
    ],
    [
      (raw) => {
        raw.approval.stages[0]?.push(raw.approval.stages[0][0] ?? "");
      },
    ],
    [
      (raw) => {
        firstChange(raw).revision = 1;
      },
    ],
    [
      (raw) => {
        firstChange(raw).amendmentRevision = 3;
      },
    ],
    [
      (raw) => {
        firstChange(raw).reason = "Substituted reason";
      },
    ],
    [
      (raw) => {
        firstChange(raw).recordedAt = "2026-10-01T01:00:03Z";
      },
    ],
    [
      (raw) => {
        raw.accounts.push({ id: amendmentId(4), displayName: "Unrelated private person" });
      },
    ],
    [
      (raw) => {
        const account = raw.accounts[0];
        if (!account) throw new Error("Review account required");
        raw.accounts.push(account);
      },
    ],
    [
      (raw) => {
        raw.availableActions.push("WITHDRAW");
      },
    ],
    [
      (raw) => {
        raw.amendment.latestReviewId = amendmentReviewId(2);
      },
    ],
  ])("rejects substituted or inconsistent review evidence %$", async (change) => {
    const raw = amendmentReviewDetails();
    change(raw);
    expect(await fixture(raw).repository.get(company, amendmentReviewId(), signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("allows a retained withdrawal after a stopped publication advances only amendment history", async () => {
    const raw = amendmentReviewDetails();
    const initial = raw.history[0];
    if (!initial) throw new Error("Submitted review change required");
    raw.amendment.version = 9;
    raw.amendment.status = "CALCULATED";
    raw.amendment.action = "WITHDRAWN";
    raw.approval.status = "CANCELLED";
    raw.approval.version = 6;
    raw.approval.currentStep = 1;
    raw.review.version = 3;
    raw.review.status = "WITHDRAWN";
    raw.availableActions = [];
    raw.history.push(
      {
        ...initial,
        revision: 1,
        amendmentRevision: 3,
        action: "APPROVED",
        approvalVersion: 1,
        step: 0,
        actorId: reviewFirst,
        decidingFor: reviewFirst,
        recordedAt: "2026-10-01T01:00:03Z",
        reason: "First review",
      },
      {
        ...initial,
        revision: 2,
        amendmentRevision: 4,
        action: "APPROVED",
        status: "APPROVED",
        approvalVersion: 2,
        step: 1,
        actorId: reviewSecond,
        decidingFor: reviewSecond,
        recordedAt: "2026-10-01T01:00:04Z",
        reason: "Independent final review",
      },
      {
        ...initial,
        revision: 3,
        amendmentRevision: 9,
        action: "WITHDRAWN",
        status: "WITHDRAWN",
        approvalVersion: 6,
        recordedAt: "2026-10-01T01:02:00Z",
        reason: "Withdraw after failed publication",
      },
    );
    expect((await fixture(raw).repository.get(company, amendmentReviewId(), signal)).ok).toBe(true);
  });
  it("bounds historical exclusions separately from the captured beneficiary", async () => {
    const raw = amendmentReviewDetails();
    raw.approval.excludedAccountIds = Array.from({ length: 201 }, (_, i) => amendmentId(i + 100));
    expect((await fixture(raw).repository.get(company, amendmentReviewId(), signal)).ok).toBe(true);
    raw.approval.excludedAccountIds.push(amendmentId(999));
    expect(await fixture(raw).repository.get(company, amendmentReviewId(), signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it.each([
    "duplicate submission",
    "missing step",
    "missing represented reviewer",
    "rejected approval",
    "invalid withdrawal",
  ])("rejects inconsistent decision history: %s", async (kind) => {
    const raw = amendmentReviewDetails(),
      initial = firstChange(raw);
    raw.amendment.version = 3;
    raw.amendment.action = "APPROVED";
    raw.review.version = 1;
    raw.approval.version = 1;
    raw.approval.currentStep = 1;
    raw.availableActions = [];
    const decision = {
      ...initial,
      revision: 1,
      amendmentRevision: 3,
      action: "APPROVED" as const,
      approvalVersion: 1,
      step: 0,
      actorId: reviewFirst,
      decidingFor: reviewFirst,
      reason: "Independent check",
      recordedAt: "2026-10-01T01:00:03Z",
    };
    raw.history.push(decision);
    expect((await fixture(raw).repository.get(company, amendmentReviewId(), signal)).ok).toBe(true);
    const changed = raw.history[1];
    if (!changed) throw new Error("Decision required");
    if (kind === "duplicate submission") changed.action = "SUBMITTED";
    if (kind === "missing step") changed.step = null;
    if (kind === "missing represented reviewer") changed.decidingFor = null;
    if (kind === "rejected approval") {
      changed.status = "REJECTED";
      raw.review.status = "REJECTED";
      raw.approval.status = "REJECTED";
    }
    if (kind === "invalid withdrawal") {
      changed.action = "WITHDRAWN";
      changed.status = "WITHDRAWN";
      raw.review.status = "WITHDRAWN";
      raw.approval.status = "CANCELLED";
    }
    expect(await fixture(raw).repository.get(company, amendmentReviewId(), signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("reads one bounded review sequence and rejects changed owners and ordering", async () => {
    const f = fixture([amendmentReview()]);
    expect((await f.repository.list(company, amendmentId(), signal)).ok).toBe(true);
    expect(f.http.request.mock.calls[0]?.[0].path).toBe(
      `/api/v1/companies/${company}/payroll/amendments/${amendmentId()}/reviews`,
    );
    for (const raw of [
      [amendmentReview({ number: 2 })],
      [amendmentReview({ amendmentId: amendmentId(2) })],
      [amendmentReview(), amendmentReview({ number: 2 })],
      Array.from({ length: 9 }, () => amendmentReview()),
    ]) {
      f.http.request.mockResolvedValueOnce(raw);
      expect(await f.repository.list(company, amendmentId(), signal)).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });
  it("preserves safe server failures and cancellation without delivering acquired private history", async () => {
    const f = fixture(amendmentReviewDetails()),
      owner = new AbortController();
    f.http.request.mockRejectedValueOnce(
      new HttpResponseError(403, {
        code: "payroll_amendment_self_preparation_forbidden",
        detail: "private",
      }),
    );
    expect(await f.repository.get(company, amendmentReviewId(), signal)).toMatchObject({
      ok: false,
      failure: { code: "payroll_amendment_self_preparation_forbidden" },
    });
    f.http.request.mockImplementationOnce(async () => {
      owner.abort();
      return amendmentReviewDetails();
    });
    await expect(
      f.repository.get(company, amendmentReviewId(), owner.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(f.http.request).toHaveBeenCalledTimes(2);
  });
});
