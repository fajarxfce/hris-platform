import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toOvertimeDetails } from "../data/mappers/overtime-details-mapper";
import {
  type OvertimeActionSnapshot,
  overtimeActionSnapshot,
} from "../domain/entities/overtime-action-snapshot";
import type { OvertimeActualTime } from "../domain/entities/overtime-actual-time";
import type { OvertimeRequestId } from "../domain/entities/overtime-request";
import type { OvertimeAction } from "../domain/entities/overtime-request-details";
import { company, identifier, overtimeDetailsDtoFixture, overtimeId } from "./overtime-fixture";
import { createWorkforceFeature } from "./workforce-feature";

const access = { companyId: company, permissions: ["overtime.manage", "overtime.approve"] };
const operation = identifier(99) as OperationId;
const actual = {
  startsAt: "2026-10-12T07:15:00Z",
  endsAt: "2026-10-12T09:45:00Z",
  breakMinutes: 15,
};
const signal = () => new AbortController().signal;

function fixture(planned = true) {
  const raw = overtimeDetailsDtoFixture(planned ? "PLANNED" : "PENDING");
  if (!planned) raw.actions.push("WITHDRAW");
  const review = overtimeActionSnapshot(
    toOvertimeDetails(raw, company, overtimeId as OvertimeRequestId, null),
  );
  const request = vi
    .fn<HttpClient["request"]>()
    .mockImplementation(async (input) =>
      input.method === "POST" ? { id: overtimeId, version: review.version + 1 } : raw,
    );
  const feature = createWorkforceFeature({ request });
  return {
    raw,
    review,
    request,
    feature,
    submit: (input: OvertimeActualTime = actual, cancellation = signal()) =>
      feature.submitOvertimeActual.execute(
        access,
        operation,
        review,
        input,
        "Work completed",
        cancellation,
      ),
    decide: (cancellation = signal()) =>
      feature.decideOvertimeRequest.execute(
        access,
        operation,
        review,
        "APPROVE",
        "Independent review",
        cancellation,
      ),
    withdraw: (cancellation = signal()) =>
      feature.withdrawOvertimeRequest.execute(
        access,
        operation,
        review,
        "No longer required",
        cancellation,
      ),
  };
}

describe("overtime action acquisition", () => {
  it("checks the server's current action and preserves its immutable command context", async () => {
    const f = fixture();
    const result = await f.feature.reviewOvertimeAction.execute(
      access,
      overtimeId,
      "SUBMIT_ACTUAL",
      signal(),
    );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    const snapshot = overtimeActionSnapshot(result.value);
    expect(snapshot).toMatchObject({ id: overtimeId, version: 0, status: "PLANNED" });
    expect(Object.isFrozen(snapshot)).toBe(true);
    expect(Object.isFrozen(snapshot.actions)).toBe(true);
    expect(Object.isFrozen(snapshot.requested)).toBe(true);
    f.raw.actions.length = 0;
    f.raw.request.requested.startsAt = "2026-10-12T08:00:00Z";
    expect(snapshot.actions).toContain("SUBMIT_ACTUAL");
    expect(snapshot.requested.startsAt).toBe("2026-10-12T07:00:00Z");
    expect("history" in snapshot).toBe(false);
  });
  it("denies missing action permissions before I/O and does not infer an action from broad read access", async () => {
    const f = fixture();
    for (const action of ["SUBMIT_ACTUAL", "APPROVE", "REJECT", "WITHDRAW"] as const) {
      expect(
        await f.feature.reviewOvertimeAction.execute(
          { ...access, permissions: ["overtime.read"] },
          overtimeId,
          action,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    expect(
      await f.feature.reviewOvertimeAction.execute(access, "../other", "SUBMIT_ACTUAL", signal()),
    ).toMatchObject({ ok: false });
    expect(
      await f.feature.reviewOvertimeAction.execute(
        access,
        overtimeId,
        "INVALID" as OvertimeAction,
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(f.request).not.toHaveBeenCalled();
    f.raw.actions = [];
    expect(
      await f.feature.reviewOvertimeAction.execute(access, overtimeId, "SUBMIT_ACTUAL", signal()),
    ).toMatchObject({ ok: false, failure: { code: "overtime_action_unavailable" } });
    expect(f.request).toHaveBeenCalledTimes(1);
  });
});

describe("overtime command boundaries", () => {
  it("submits only original version, UTC time and reason, with the caller's stable operation key", async () => {
    const f = fixture();
    expect(await f.submit()).toEqual({ ok: true, value: { id: overtimeId, version: 1 } });
    const sent = f.request.mock.calls[0]?.[0];
    expect(sent).toEqual({
      path: `/api/v1/companies/${company}/workforce/overtime/${overtimeId}/actual`,
      method: "POST",
      operationId: operation,
      body: { expectedVersion: 0, actual, reason: "Work completed" },
    });
    f.raw.request.status = "PENDING";
    f.raw.request.version = 1;
    f.raw.actions = [];
    expect(await f.submit()).toEqual({ ok: true, value: { id: overtimeId, version: 1 } });
    expect(f.request.mock.calls[1]?.[0]).toEqual(sent);
    expect(f.request.mock.calls.every(([request]) => request.method === "POST")).toBe(true);
  });
  it("keeps decisions and withdrawal distinct and checks their receipt version", async () => {
    const f = fixture(false);
    expect(await f.decide()).toEqual({ ok: true, value: { id: overtimeId, version: 2 } });
    expect(f.request.mock.calls[0]?.[0]).toMatchObject({
      path: `/api/v1/companies/${company}/workforce/overtime/${overtimeId}/decisions`,
      method: "POST",
      operationId: operation,
      body: { expectedVersion: 1, decision: "APPROVE", reason: "Independent review" },
    });
    expect(await f.withdraw()).toEqual({ ok: true, value: { id: overtimeId, version: 2 } });
    expect(f.request.mock.calls[1]?.[0]).toMatchObject({
      path: `/api/v1/companies/${company}/workforce/overtime/${overtimeId}/withdraw`,
      method: "POST",
      operationId: operation,
      body: { expectedVersion: 1, reason: "No longer required" },
    });
  });
  it("rejects company mismatch, missing capabilities and changed status without sending commands", async () => {
    const f = fixture(false);
    for (const review of [
      { ...f.review, companyId: identifier(999) as CompanyId },
      { ...f.review, actions: [] },
      { ...f.review, status: "APPROVED" as const },
    ]) {
      expect(
        (
          await f.feature.decideOvertimeRequest.execute(
            access,
            operation,
            review,
            "APPROVE",
            "",
            signal(),
          )
        ).ok,
      ).toBe(false);
      expect(
        (
          await f.feature.withdrawOvertimeRequest.execute(
            access,
            operation,
            review,
            "Reason",
            signal(),
          )
        ).ok,
      ).toBe(false);
    }
    expect(
      (
        await f.feature.decideOvertimeRequest.execute(
          { ...access, permissions: ["overtime.manage"] },
          operation,
          f.review,
          "APPROVE",
          "",
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each([
    { version: -1 },
    { version: 0.5 },
    { version: 999 },
    { version: Number.NaN },
    { id: "../other" },
  ])("rejects invalid observed facts: %j", async (change) => {
    const f = fixture(false),
      review = { ...f.review, ...change } as OvertimeActionSnapshot;
    expect(
      await f.feature.decideOvertimeRequest.execute(
        access,
        operation,
        review,
        "APPROVE",
        "",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_overtime_decision" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it("requires a bounded reason for rejection, withdrawal and actual submission", async () => {
    const pending = fixture(false),
      planned = fixture();
    for (const reason of ["   ", "x".repeat(1001)]) {
      expect(
        (
          await pending.feature.decideOvertimeRequest.execute(
            access,
            operation,
            pending.review,
            "REJECT",
            reason,
            signal(),
          )
        ).ok,
      ).toBe(false);
      expect(
        (
          await pending.feature.withdrawOvertimeRequest.execute(
            access,
            operation,
            pending.review,
            reason,
            signal(),
          )
        ).ok,
      ).toBe(false);
      expect(
        (
          await planned.feature.submitOvertimeActual.execute(
            access,
            operation,
            planned.review,
            actual,
            reason,
            signal(),
          )
        ).ok,
      ).toBe(false);
    }
    expect(pending.request).not.toHaveBeenCalled();
    expect(planned.request).not.toHaveBeenCalled();
    expect(
      (
        await pending.feature.decideOvertimeRequest.execute(
          access,
          operation,
          pending.review,
          "APPROVE",
          "",
          signal(),
        )
      ).ok,
    ).toBe(true);
  });
  it("does not let caller mutation replace a pending command's time", async () => {
    const f = fixture(),
      input = { ...actual };
    let resolve!: (value: unknown) => void;
    f.request.mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const pending = f.submit(input);
    input.startsAt = "2026-10-12T00:00:00Z";
    input.breakMinutes = 100;
    expect(f.request.mock.calls[0]?.[0].body).toEqual({
      expectedVersion: 0,
      actual,
      reason: "Work completed",
    });
    resolve({ id: overtimeId, version: 1 });
    expect((await pending).ok).toBe(true);
  });
  it.each([
    { id: identifier(999), version: 1 },
    { id: overtimeId, version: 0 },
    { id: overtimeId, version: 2 },
    { id: overtimeId, version: -1 },
    { id: overtimeId, version: 1000 },
    { id: overtimeId, version: 1.5 },
  ])("rejects a mismatched or malformed receipt: %j", async (receipt) => {
    const f = fixture();
    f.request.mockResolvedValue(receipt);
    expect(await f.submit()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
});

describe("actual overtime interval", () => {
  it.each([
    { startsAt: "2026-10-12T07:15:01Z" },
    { startsAt: "2026-10-12T07:15:00.000000001Z" },
    { startsAt: "2026-02-30T07:15:00Z" },
    { startsAt: "2026-10-12T14:15:00+07:00" },
    { endsAt: "2026-10-12T07:15:00Z" },
    { endsAt: "2026-10-12T20:00:00Z" },
    { breakMinutes: -1 },
    { breakMinutes: 121 },
    { breakMinutes: 0.5 },
    { endsAt: "2026-10-12T07:30:00Z", breakMinutes: 15 },
  ])("rejects invalid duration, precision or break before transport: %j", async (change) => {
    const f = fixture();
    expect(await f.submit({ ...actual, ...change })).toMatchObject({
      ok: false,
      failure: { code: "invalid_overtime_interval" },
    });
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each([{ startsAt: "2026-10-12T06:59:00Z" }, { endsAt: "2026-10-12T10:01:00Z" }])(
    "rejects actual time outside the requested window: %j",
    async (change) => {
      const f = fixture();
      expect(await f.submit({ ...actual, ...change })).toMatchObject({
        ok: false,
        failure: { code: "overtime_outside_requested_window" },
      });
      expect(f.request).not.toHaveBeenCalled();
    },
  );
  it("compares net minutes and accepts the inclusive twelve-hour/two-hour-break boundary", async () => {
    const f = fixture();
    const restricted = {
      ...f.review,
      requested: { ...f.review.requested, breakMinutes: 120, workedMinutes: 60 },
    };
    expect(
      await f.feature.submitOvertimeActual.execute(
        access,
        operation,
        restricted,
        actual,
        "Completed",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "overtime_outside_requested_window" } });
    const extended = {
      ...f.review,
      requested: {
        startsAt: "2026-10-12T00:00:00Z",
        endsAt: "2026-10-12T12:00:00Z",
        breakMinutes: 0,
        workedMinutes: 720,
      },
    };
    expect(
      (
        await f.feature.submitOvertimeActual.execute(
          access,
          operation,
          extended,
          {
            startsAt: extended.requested.startsAt,
            endsAt: extended.requested.endsAt,
            breakMinutes: 120,
          },
          "Completed",
          signal(),
        )
      ).ok,
    ).toBe(true);
    expect(f.request).toHaveBeenCalledTimes(1);
  });
});

describe("overtime failure and cancellation", () => {
  it("preserves API error codes, safe fields and correlation while excluding raw diagnostic text", async () => {
    const f = fixture();
    f.request.mockRejectedValue(
      new HttpResponseError(403, {
        code: "mfa_required",
        fields: {},
        parameters: {},
        correlationId: identifier(77),
        detail: "PRIVATE",
      }),
    );
    const result = await f.submit();
    expect(result).toEqual({
      ok: false,
      failure: { code: "mfa_required", fields: {}, parameters: {}, correlationId: identifier(77) },
    });
    expect(f.request).toHaveBeenCalledTimes(1);
  });
  for (const kind of ["submit", "decide", "withdraw", "review"] as const) {
    it(`cancels ${kind} before I/O and rejects a late success or failure`, async () => {
      for (const lateFailure of [false, true]) {
        const f = fixture(kind === "submit" || kind === "withdraw"),
          controller = new AbortController();
        const execute = (signal: AbortSignal) =>
          kind === "submit"
            ? f.submit(actual, signal)
            : kind === "decide"
              ? f.decide(signal)
              : kind === "withdraw"
                ? f.withdraw(signal)
                : f.feature.reviewOvertimeAction.execute(access, overtimeId, "APPROVE", signal);
        const aborted = new AbortController();
        aborted.abort();
        await expect(async () => execute(aborted.signal)).rejects.toMatchObject({
          name: "AbortError",
        });
        expect(f.request).not.toHaveBeenCalled();
        let resolve!: (value: unknown) => void, reject!: (reason: unknown) => void;
        f.request.mockImplementation(
          () =>
            new Promise((done, fail) => {
              resolve = done;
              reject = fail;
            }),
        );
        const pending = execute(controller.signal);
        controller.abort();
        if (lateFailure) reject(new HttpResponseError(403, { code: "access_denied" }));
        else resolve(kind === "review" ? f.raw : { id: overtimeId, version: f.review.version + 1 });
        await expect(pending).rejects.toMatchObject({ name: "AbortError" });
        expect(f.request).toHaveBeenCalledTimes(1);
      }
    });
  }
});
