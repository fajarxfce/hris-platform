import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId, CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { ApprovalDelegationChange } from "../domain/entities/approval-delegation-change";
import { validApprovalDelegationPeriod } from "../domain/policies/approval-delegation-policy";
import { createApprovalsFeature } from "./approvals-feature";

const company = "10000000-abcd-4000-8000-000000000001" as CompanyId;
const actor = "20000000-abcd-4000-8000-000000000001" as AccountId;
const delegate = "20000000-abcd-4000-8000-000000000002" as AccountId;
const other = "20000000-abcd-4000-8000-000000000003" as AccountId;
const id = "90000000-abcd-4000-8000-000000000001";
const operation = "30000000-abcd-4000-8000-000000000001" as OperationId;
const access = { companyId: company, permissions: ["approvals.read"] };
const signal = () => new AbortController().signal;
const row = {
  id,
  kind: "LEAVE" as const,
  fromAccount: actor,
  toAccount: delegate,
  validFrom: "2026-10-01T00:00:00.123456Z",
  validUntil: "2026-10-10T00:00:00.123456Z",
  active: false,
  version: 1,
};
const change = (): ApprovalDelegationChange => ({
  ...row,
  expectedVersion: 1,
  reason: "Coverage handover",
});

describe("approval delegation boundary", () => {
  it("checks permissions, ownership and bounded input before writes", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createApprovalsFeature({ request });
    const denied = { ...access, permissions: ["approvals.manage"] };
    expect(await feature.loadDelegations.execute(denied, actor, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "access_denied" },
    });
    expect(await feature.loadDelegation.execute(denied, actor, id, signal())).toMatchObject({
      ok: false,
      failure: { code: "access_denied" },
    });
    expect(await feature.loadDelegationForEdit.execute(denied, actor, id, signal())).toMatchObject({
      ok: false,
      failure: { code: "access_denied" },
    });
    expect(
      await feature.saveDelegation.execute(access, delegate, operation, change(), signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await feature.loadDelegations.execute(access, actor, "../other", signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    expect(await feature.loadDelegation.execute(access, actor, "bad", signal())).toMatchObject({
      ok: false,
      failure: { code: "approval_delegation_not_found" },
    });
    for (const invalid of [
      { toAccount: actor },
      { reason: "  " },
      { reason: "x".repeat(1001) },
      { validUntil: row.validFrom },
      { validUntil: "2027-10-01T00:00:00Z" },
      { validFrom: "2026-02-30T00:00:00Z" },
      { expectedVersion: -1 },
      { expectedVersion: Number.MAX_SAFE_INTEGER },
    ])
      expect(
        await feature.saveDelegation.execute(
          access,
          actor,
          operation,
          { ...change(), ...invalid },
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_delegation" } });
    expect(request).not.toHaveBeenCalled();
  });
  it("retains expired details and separates recipient viewing from editing", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ ...row });
    const feature = createApprovalsFeature({ request });
    expect(await feature.loadDelegation.execute(access, delegate, id, signal())).toMatchObject({
      ok: true,
      value: { companyId: company, active: false },
    });
    expect(
      await feature.loadDelegationForEdit.execute(access, delegate, id, signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(await feature.loadDelegation.execute(access, other, id, signal())).toMatchObject({
      ok: false,
      failure: { code: "approval_delegation_not_found" },
    });
    const result = await feature.loadDelegationForEdit.execute(
      { ...access, permissions: [...access.permissions, "approvals.manage"] },
      other,
      id.toUpperCase(),
      signal(),
    );
    expect(result).toMatchObject({ ok: true, value: { id, validFrom: row.validFrom } });
    if (!result.ok) throw new Error("Expected retained delegation");
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${company}/approvals/delegations/${id}`,
    );
  });
  it("requires an ascending account-owned page and rejects bad cursors or foreign projections", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createApprovalsFeature({ request });
    const items = Array.from({ length: 20 }, (_, index) => ({
      ...row,
      id: `90000000-abcd-4000-8000-${String(index + 2).padStart(12, "0")}`,
    }));
    request.mockResolvedValueOnce({ items, nextCursor: items.at(-1)?.id });
    const page = await feature.loadDelegations.execute(access, actor, id.toUpperCase(), signal());
    expect(page).toMatchObject({ ok: true, value: { nextCursor: items.at(-1)?.id } });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${company}/approvals/delegations?limit=20&after=${id}`,
    );
    if (!page.ok) throw new Error("Expected owned page");
    expect(Object.isFrozen(page.value.items)).toBe(true);
    for (const bad of [
      { items: [row, row], nextCursor: null },
      { items: [row], nextCursor: row.id },
      { items: [{ ...row, fromAccount: other }], nextCursor: null },
      { items: Array.from({ length: 21 }, () => row), nextCursor: null },
    ]) {
      request.mockResolvedValueOnce(bad);
      expect(await feature.loadDelegations.execute(access, actor, null, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });
  it("normalizes one versioned command and verifies the returned receipt", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ id, version: 2 });
    const save = createApprovalsFeature({ request }).saveDelegation;
    const input = {
      ...change(),
      id: id.toUpperCase(),
      fromAccount: actor.toUpperCase() as AccountId,
      reason: "  Coverage handover  ",
    };
    expect(await save.execute(access, actor, operation, input, signal())).toEqual({
      ok: true,
      value: { id, version: 2 },
    });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${company}/approvals/delegations/${id}`,
      method: "PUT",
      operationId: operation,
      body: {
        kind: row.kind,
        fromAccount: actor,
        toAccount: delegate,
        validFrom: row.validFrom,
        validUntil: row.validUntil,
        active: false,
        expectedVersion: 1,
        reason: "Coverage handover",
      },
    });
    for (const receipt of [
      { id, version: 1 },
      { id: other, version: 2 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(await save.execute(access, actor, operation, change(), signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    expect(input.reason).toBe("  Coverage handover  ");
  });
  it("maps malformed data and preserves technical cancellation without leaking raw failures", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createApprovalsFeature({ request });
    for (const bad of [
      { ...row, validFrom: "2026-02-30T00:00:00Z" },
      { ...row, toAccount: actor },
      { ...row, version: Number.MAX_SAFE_INTEGER + 1 },
      { ...row, id: other },
    ]) {
      request.mockResolvedValueOnce(bad);
      expect(await feature.loadDelegation.execute(access, actor, id, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    request.mockRejectedValueOnce(
      new HttpResponseError(422, {
        code: "approver_unavailable",
        detail: "PRIVATE DETAILS",
        fields: {},
        parameters: {},
      }),
    );
    const failure = await feature.saveDelegation.execute(
      access,
      actor,
      operation,
      change(),
      signal(),
    );
    expect(failure).toMatchObject({ ok: false, failure: { code: "approver_unavailable" } });
    expect(JSON.stringify(failure)).not.toContain("PRIVATE");
    const pending = new AbortController();
    pending.abort();
    await expect(
      feature.loadDelegation.execute(access, actor, id, pending.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    request.mockRejectedValueOnce(new DOMException("Cancelled", "AbortError"));
    await expect(
      feature.loadDelegationForEdit.execute(access, actor, id, signal()),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
  it("measures a 90-day period at stored precision, including a DST-spanning extra hour", () => {
    expect(
      validApprovalDelegationPeriod("2026-01-01T00:00:00.000001Z", "2026-04-01T00:00:00.000001Z"),
    ).toBe(true);
    expect(
      validApprovalDelegationPeriod("2026-01-01T00:00:00.000001Z", "2026-04-01T00:00:00.000002Z"),
    ).toBe(false);
    expect(validApprovalDelegationPeriod("2026-10-01T04:00:00Z", "2026-12-30T05:00:00Z")).toBe(
      false,
    );
  });
});
