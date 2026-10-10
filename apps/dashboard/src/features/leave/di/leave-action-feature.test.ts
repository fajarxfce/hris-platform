import { describe, expect, it, vi } from "vitest";
import { leaveRecord } from "../../../../tests/fixtures/leave";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toLeaveRequestDetails } from "../data/mappers/leave-request-details-mapper";
import type { LeaveRequestId } from "../domain/entities/leave-request";
import type { LeaveActionSnapshot } from "../domain/entities/leave-request-action";
import { createLeaveFeature } from "./leave-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["leave.approve", "leave.manage"] };
const operation = "90000000-0000-4000-8000-000000000001" as OperationId;
const signal = () => new AbortController().signal;
function snapshot(
  status: "PENDING" | "APPROVED" | "CANCELLATION_PENDING" = "PENDING",
): LeaveActionSnapshot {
  const request = leaveRecord();
  return {
    id: request.id as LeaveRequestId,
    companyId,
    version: 3,
    status,
    availableActions: status === "APPROVED" ? ["REQUEST_CANCELLATION"] : ["DECIDE", "WITHDRAW"],
  };
}
describe("leave action boundaries", () => {
  it("checks current grants, identifiers and company scope before acquisition or commands", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    const readOnly = { ...access, permissions: ["leave.read"] };
    const review = snapshot();
    for (const intent of ["approve", "reject", "withdraw", "cancel"] as const)
      expect(
        await feature.reviewAction.execute(readOnly, review.id, intent, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await feature.decide.execute(readOnly, operation, review, "APPROVE", "", signal()),
    ).toMatchObject({ ok: false });
    expect(
      await feature.withdraw.execute(readOnly, operation, review, "Plans changed", signal()),
    ).toMatchObject({ ok: false });
    expect(
      await feature.requestCancellation.execute(
        readOnly,
        operation,
        snapshot("APPROVED"),
        "Plans changed",
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(
      await feature.reviewAction.execute(access, "../foreign", "approve", signal()),
    ).toMatchObject({ ok: false });
    expect(
      await feature.decide.execute(
        access,
        operation,
        { ...review, companyId: "other" as CompanyId },
        "APPROVE",
        "",
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
  });
  it("reads a fresh resource before review and uses server assignment rather than broad read grants", async () => {
    const record = leaveRecord();
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(record);
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    const teamApprover = { ...access, permissions: ["leave.team.approve"] };
    expect(
      await feature.reviewAction.execute(
        teamApprover,
        record.id.toUpperCase(),
        "approve",
        signal(),
      ),
    ).toMatchObject({ ok: true, value: { version: 0 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/leave/requests/${record.id}?historyLimit=20`,
    });
    request.mockResolvedValue({ ...record, availableActions: [] });
    expect(
      await feature.reviewAction.execute(teamApprover, record.id, "approve", signal()),
    ).toMatchObject({ ok: false, failure: { code: "leave_action_unavailable" } });
    expect(
      await feature.decide.execute(
        access,
        operation,
        { ...snapshot(), availableActions: [] },
        "APPROVE",
        "",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "leave_action_unavailable" } });
    expect(request).toHaveBeenCalledTimes(2);
  });
  it.each(["approve", "reject", "withdraw", "cancel"] as const)(
    "sends only the original %s command and replays without a new read",
    async (intent) => {
      const review = snapshot(intent === "cancel" ? "APPROVED" : "CANCELLATION_PENDING");
      const request = vi
        .fn<HttpClient["request"]>()
        .mockResolvedValue({ id: review.id, version: 4 });
      const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
      const execute = () =>
        intent === "approve" || intent === "reject"
          ? feature.decide.execute(
              access,
              operation,
              review,
              intent === "approve" ? "APPROVE" : "REJECT",
              "  Reviewed schedule  ",
              signal(),
            )
          : intent === "withdraw"
            ? feature.withdraw.execute(access, operation, review, "  Reviewed schedule  ", signal())
            : feature.requestCancellation.execute(
                access,
                operation,
                review,
                "  Reviewed schedule  ",
                signal(),
              );
      expect(await execute()).toEqual({ ok: true, value: { id: review.id, version: 4 } });
      expect(await execute()).toEqual({ ok: true, value: { id: review.id, version: 4 } });
      const endpoint =
        intent === "cancel" ? "cancellation" : intent === "withdraw" ? "withdraw" : "decisions";
      for (const call of request.mock.calls)
        expect(call[0]).toEqual({
          path: `/api/v1/companies/${companyId}/leave/requests/${review.id}/${endpoint}`,
          method: "POST",
          operationId: operation,
          body: {
            version: 3,
            reason: "Reviewed schedule",
            ...(intent === "approve" || intent === "reject"
              ? { decision: intent.toUpperCase() }
              : {}),
          },
        });
      expect(request).toHaveBeenCalledTimes(2);
    },
  );
  it("bounds reasons and observed versions and requires a reason for rejection, withdrawal and cancellation", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: snapshot().id, version: 4 });
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    for (const reason of ["", "  ", "x".repeat(1001)]) {
      expect(
        await feature.decide.execute(access, operation, snapshot(), "REJECT", reason, signal()),
      ).toMatchObject({ ok: false });
      expect(
        await feature.withdraw.execute(access, operation, snapshot(), reason, signal()),
      ).toMatchObject({ ok: false });
      expect(
        await feature.requestCancellation.execute(
          access,
          operation,
          snapshot("APPROVED"),
          reason,
          signal(),
        ),
      ).toMatchObject({ ok: false });
    }
    for (const version of [-1, 0.5, Number.MAX_SAFE_INTEGER, Number.NaN])
      expect(
        await feature.decide.execute(
          access,
          operation,
          { ...snapshot(), version },
          "APPROVE",
          "",
          signal(),
        ),
      ).toMatchObject({ ok: false });
    expect(
      await feature.decide.execute(
        access,
        "invalid" as OperationId,
        snapshot(),
        "APPROVE",
        "",
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
    expect(
      await feature.decide.execute(access, operation, snapshot(), "APPROVE", "", signal()),
    ).toMatchObject({ ok: true });
  });
  it("rejects unavailable or terminal actions even with forged available-action flags", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    const approved = { ...snapshot(), status: "APPROVED" as const };
    expect(
      await feature.decide.execute(access, operation, approved, "APPROVE", "", signal()),
    ).toMatchObject({ ok: false });
    expect(
      await feature.withdraw.execute(access, operation, approved, "Changed", signal()),
    ).toMatchObject({ ok: false });
    expect(
      await feature.requestCancellation.execute(
        access,
        operation,
        { ...snapshot(), availableActions: ["REQUEST_CANCELLATION"] },
        "Changed",
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
  });
  it("checks receipt identity/version and preserves safe failures and cancellation", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    for (const receipt of [
      { id: snapshot().id, version: 3 },
      { id: operation, version: 4 },
      { id: snapshot().id, version: 5 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(
        await feature.withdraw.execute(access, operation, snapshot(), "Changed", signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    request.mockRejectedValueOnce(
      new HttpResponseError(403, {
        code: "not_assigned_approver",
        fields: {},
        parameters: {},
        detail: "PRIVATE",
      }),
    );
    const result = await feature.decide.execute(
      access,
      operation,
      snapshot(),
      "REJECT",
      "Changed",
      signal(),
    );
    expect(result).toMatchObject({ ok: false, failure: { code: "not_assigned_approver" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() =>
      feature.withdraw.execute(access, operation, snapshot(), "Changed", cancelled.signal),
    ).toThrow();
    request.mockRejectedValueOnce(new DOMException("Cancelled", "AbortError"));
    await expect(
      feature.withdraw.execute(access, operation, snapshot(), "Changed", signal()),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
  it("does not publish a review that completed after cancellation", async () => {
    const cancelled = new AbortController();
    const record = leaveRecord();
    const request = vi.fn<HttpClient["request"]>().mockImplementation(async () => {
      cancelled.abort();
      return record;
    });
    await expect(
      createLeaveFeature({ request }, { downloadBinary: vi.fn() }).reviewAction.execute(
        access,
        record.id,
        "approve",
        cancelled.signal,
      ),
    ).rejects.toMatchObject({ name: "AbortError" });
    const mapped = toLeaveRequestDetails(record, companyId, null);
    expect(Object.isFrozen(mapped.availableActions)).toBe(true);
  });
});
