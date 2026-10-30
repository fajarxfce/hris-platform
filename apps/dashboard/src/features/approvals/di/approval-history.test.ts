import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { createApprovalsFeature } from "./approvals-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "30000000-0000-4000-8000-000000000001";
const actor = "20000000-0000-4000-8000-000000000001";
const delegate = "20000000-0000-4000-8000-000000000002";
const access = { companyId: company, permissions: [] };
const signal = () => new AbortController().signal;
const change = (revision: number) => ({
  revision,
  step: 0,
  assignees: [actor],
  actorId: delegate,
  reason: "Independent reviewer",
  recordedAt: "2026-10-01T09:20:00.123456Z",
});
const row = () => ({
  request: {
    id,
    kind: "LEAVE",
    resourceId: "40000000-0000-4000-8000-000000000001",
    authorId: actor,
    requesterId: null,
    templateId: "50000000-0000-4000-8000-000000000001",
    templateRevision: 0,
    stages: [{ assignees: [actor] }],
    currentStep: 0,
    status: "APPROVED",
    version: 52,
    submittedAt: "2026-10-01T09:00:00Z",
    excludedAccountIds: [],
  },
  decisions: [
    {
      step: 0,
      actorId: delegate,
      decidingFor: actor,
      decision: "APPROVE",
      reason: "Reviewed",
      decidedAt: "2026-10-01T09:30:00.123456Z",
    },
  ],
  reassignments: { items: [change(51)], nextCursor: null as string | null },
});

describe("approval history boundary", () => {
  it("leaves author authorization to the server and pins normalized continuation/version", async () => {
    const dto = row();
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const load = createApprovalsFeature({ request }).loadHistory;
    const result = await load.execute(access, id.toUpperCase(), 50, 52, signal());
    expect(result).toMatchObject({
      ok: true,
      value: {
        request: { id, companyId: company },
        decisions: [{ actorId: delegate, decidingFor: actor }],
        reassignments: { nextCursor: null },
      },
    });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${company}/approvals/${id}/history?limit=50&after=50&expectedVersion=52`,
    );
    if (!result.ok) throw new Error("Expected history");
    const decision = dto.decisions[0],
      reassignment = dto.reassignments.items[0];
    if (!decision || !reassignment) throw new Error("Missing retained fixture facts");
    decision.reason = "Changed";
    reassignment.assignees.splice(0);
    expect(result.value.decisions[0]?.reason).toBe("Reviewed");
    expect(result.value.reassignments.items[0]?.assignees).toEqual([actor]);
    for (const value of [
      result.value,
      result.value.decisions,
      result.value.decisions[0],
      result.value.reassignments,
      result.value.reassignments.items,
      result.value.reassignments.items[0]?.assignees,
    ])
      expect(Object.isFrozen(value)).toBe(true);
  });
  it("rejects invalid identifiers and unsafe continuation before acquisition", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const load = createApprovalsFeature({ request }).loadHistory;
    expect(await load.execute(access, "../private", null, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "approval_not_found" },
    });
    for (const [after, version] of [
      [1, null],
      [0, 1],
      [-1, 1],
      [1.5, 2],
      [Number.MAX_SAFE_INTEGER + 1, 2],
      [null, -1],
      [null, Number.NaN],
      [null, 2.5],
    ])
      expect(
        await load.execute(access, id, after ?? null, version ?? null, signal()),
      ).toMatchObject({
        ok: false,
        failure: { code: "invalid_approval_history_query" },
      });
    expect(request).not.toHaveBeenCalled();
  });
  it("rejects malformed, oversized, unordered and foreign retained facts", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const load = createApprovalsFeature({ request }).loadHistory;
    const dto = row(),
      decision = dto.decisions[0];
    if (!decision) throw new Error("Missing retained decision fixture");
    for (const bad of [
      { ...dto, request: { ...dto.request, id: actor } },
      { ...dto, request: { ...dto.request, version: 53 } },
      { ...dto, decisions: [decision, decision] },
      { ...dto, decisions: [{ ...decision, step: 1 }] },
      { ...dto, decisions: Array.from({ length: 9 }, () => decision) },
      { ...dto, decisions: [{ ...decision, decidedAt: "2026-02-30T09:30:00Z" }] },
      { ...dto, decisions: [{ ...decision, reason: "x".repeat(1001) }] },
      { ...dto, decisions: [{ ...decision, decision: "CANCEL" }] },
      { ...dto, reassignments: { items: [change(51), change(51)], nextCursor: null } },
      { ...dto, reassignments: { items: [change(52), change(51)], nextCursor: null } },
      { ...dto, reassignments: { items: [change(50)], nextCursor: null } },
      { ...dto, reassignments: { items: [change(53)], nextCursor: null } },
      {
        ...dto,
        reassignments: { items: [{ ...change(51), assignees: [actor, actor] }], nextCursor: null },
      },
      { ...dto, reassignments: { items: [{ ...change(51), assignees: [] }], nextCursor: null } },
      { ...dto, reassignments: { items: [change(51)], nextCursor: "51" } },
    ]) {
      request.mockResolvedValueOnce(bad);
      expect(await load.execute(access, id, 50, 52, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    const items = Array.from({ length: 50 }, (_, index) => change(index + 1));
    request.mockResolvedValueOnce({ ...dto, reassignments: { items, nextCursor: "50" } });
    expect(await load.execute(access, id, null, null, signal())).toMatchObject({
      ok: true,
      value: { reassignments: { nextCursor: "50" } },
    });
    for (const nextCursor of ["49", "050", "9007199254740992"]) {
      request.mockResolvedValueOnce({ ...dto, reassignments: { items, nextCursor } });
      expect(await load.execute(access, id, null, null, signal())).toMatchObject({ ok: false });
    }
    request.mockResolvedValueOnce({
      ...dto,
      reassignments: { items: [...items, change(51)], nextCursor: null },
    });
    expect(await load.execute(access, id, null, null, signal())).toMatchObject({ ok: false });
  });
  it("preserves explicit conflicts and cancellation without leaking raw errors", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(409, {
        code: "approval_changed",
        detail: "PRIVATE DATA",
        fields: {},
        parameters: {},
      }),
    );
    const load = createApprovalsFeature({ request }).loadHistory;
    const result = await load.execute(access, id, 50, 52, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "approval_changed" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    const pending = new AbortController();
    pending.abort();
    expect(() => load.execute(access, id, null, null, pending.signal)).toThrow();
    request.mockRejectedValueOnce(new DOMException("Cancelled", "AbortError"));
    await expect(load.execute(access, id, null, null, signal())).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});
