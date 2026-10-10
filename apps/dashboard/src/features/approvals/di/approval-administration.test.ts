import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId, CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { ApprovalTemplateChange } from "../domain/entities/approval-template-change";
import { createApprovalsFeature } from "./approvals-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "50000000-0000-4000-8000-000000000001";
const actor = "20000000-0000-4000-8000-000000000001" as AccountId;
const operation = "60000000-0000-4000-8000-000000000001" as OperationId;
const access = { companyId, permissions: ["approvals.manage"] };
const signal = () => new AbortController().signal;
const row = {
  id,
  name: "Expense review",
  kind: "EXPENSE",
  active: true,
  version: 2,
  appliedRevision: 1,
  effectiveFrom: "2026-10-01",
  category: "TRAVEL",
  minimumAmount: "1234567890123456.78",
  stages: [{ assignment: "NAMED", accountIds: [actor], permission: null }],
};
const change = (): ApprovalTemplateChange => ({
  id,
  name: "Expense review",
  kind: "EXPENSE",
  active: true,
  expectedVersion: 2,
  effectiveFrom: "2026-10-01",
  category: "TRAVEL",
  minimumAmount: "1234567890123456.78",
  reason: "Annual policy review",
  stages: [{ assignment: "NAMED", accountIds: [actor], permission: null }],
});

describe("approval administration boundary", () => {
  it("keeps an accepted whole threshold valid after PostgreSQL adds fractional zeros", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ id, version: 3 });
    const feature = createApprovalsFeature({ request });
    for (const minimumAmount of [
      "999999999999999999",
      "999999999999999999.00",
      "99999999999999999.10",
    ])
      expect(
        await feature.saveTemplate.execute(
          access,
          operation,
          { ...change(), minimumAmount },
          signal(),
        ),
      ).toMatchObject({ ok: true });
    expect(request.mock.lastCall?.[0].body).toMatchObject({
      minimumAmount: "99999999999999999.10",
    });
  });
  it("normalizes only transport details and retains exact money and selected revision", async () => {
    const dto = structuredClone(row);
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const result = await createApprovalsFeature({ request }).loadTemplate.execute(
      access,
      id,
      "1",
      signal(),
    );
    expect(result).toMatchObject({
      ok: true,
      value: { id, companyId, version: 2, revision: 1, minimumAmount: "1234567890123456.78" },
    });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/approvals/templates/${id}?revision=1`,
    );
    if (!result.ok) throw new Error("Expected a template");
    dto.stages[0]?.accountIds.splice(0);
    expect(result.value).not.toHaveProperty("appliedRevision");
    expect(result.value.stages[0]?.accountIds).toEqual([actor]);
    for (const value of [
      result.value,
      result.value.stages,
      result.value.stages[0],
      result.value.stages[0]?.accountIds,
    ])
      expect(Object.isFrozen(value)).toBe(true);
  });
  it("sends one versioned command, without display names or revision aliases", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ id, version: 3 });
    const input = {
      ...change(),
      name: "  Expense review ",
      category: " TRAVEL ",
      reason: " Annual policy review ",
    };
    expect(
      await createApprovalsFeature({ request }).saveTemplate.execute(
        access,
        operation,
        input,
        signal(),
      ),
    ).toEqual({ ok: true, value: { id, version: 3 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/approvals/templates/${id}`,
      method: "PUT",
      operationId: operation,
      body: {
        name: "Expense review",
        kind: "EXPENSE",
        active: true,
        expectedVersion: 2,
        effectiveFrom: "2026-10-01",
        category: "TRAVEL",
        minimumAmount: "1234567890123456.78",
        reason: "Annual policy review",
        stages: row.stages,
      },
    });
  });
  it("rejects permissions, malformed queries and illegal stages before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createApprovalsFeature({ request });
    expect(
      await feature.loadTemplates.execute(
        { ...access, permissions: ["approvals.read"] },
        { kind: "EXPENSE", asOf: "2026-10-01", after: null },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const revision of ["-1", "01", "1.1", "1e2", "9007199254740992"])
      expect(await feature.loadTemplate.execute(access, id, revision, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_revision" },
      });
    for (const input of [
      { ...change(), minimumAmount: "1e5" },
      { ...change(), minimumAmount: "1.001" },
      { ...change(), minimumAmount: "-1" },
      { ...change(), minimumAmount: "999999999999999999.99" },
      { ...change(), effectiveFrom: "2026-02-30" },
      { ...change(), reason: " " },
      { ...change(), stages: [] },
      { ...change(), stages: Array(9).fill(change().stages[0]) },
      {
        ...change(),
        stages: [{ assignment: "NAMED" as const, accountIds: [actor, actor], permission: null }],
      },
      {
        ...change(),
        stages: [
          { assignment: "PERMISSION" as const, accountIds: [], permission: "company.manage" },
        ],
      },
      {
        ...change(),
        kind: "PAYROLL" as const,
        stages: [{ assignment: "MANAGER" as const, accountIds: [], permission: null }],
      },
    ])
      expect(await feature.saveTemplate.execute(access, operation, input, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_approval_template" },
      });
    expect(request).not.toHaveBeenCalled();
  });
  it("allows only the relevant review grant or administration for account lookup", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ items: [{ id: actor, displayName: "Reviewer" }], nextCursor: null });
    const load = createApprovalsFeature({ request }).loadAssignees;
    const search = { kind: "EXPENSE" as const, query: " Scope_100% ", after: null };
    for (const permissions of [
      [],
      ["approvals.read"],
      ["expenses.approve"],
      ["approvals.read", "leave.approve"],
    ])
      expect(await load.execute({ companyId, permissions }, search, signal())).toMatchObject({
        ok: false,
        failure: { code: "access_denied" },
      });
    expect(request).not.toHaveBeenCalled();
    for (const permissions of [["approvals.manage"], ["approvals.read", "expenses.team.approve"]])
      expect(await load.execute({ companyId, permissions }, search, signal())).toMatchObject({
        ok: true,
        value: { items: [{ id: actor, displayName: "Reviewer" }] },
      });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/approvals/assignees?kind=EXPENSE&query=Scope_100%25&limit=10`,
    );
  });
  it("checks page ordering, filter consistency, explicit revisions and receipt identity", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createApprovalsFeature({ request });
    for (const dto of [
      { ...row, appliedRevision: 3 },
      { ...row, effectiveFrom: "2026-02-30" },
      { ...row, id: operation },
      { ...row, stages: [{ ...row.stages[0], accountIds: [actor, actor] }] },
      { ...row, appliedRevision: 0 },
    ]) {
      request.mockResolvedValueOnce(dto);
      expect(await feature.loadTemplate.execute(access, id, "1", signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    for (const page of [
      { items: [row, row], nextCursor: null },
      { items: [{ ...row, kind: "LEAVE" }], nextCursor: null },
      { items: [row], nextCursor: id },
      { items: [{ ...row, effectiveFrom: "2027-01-01" }], nextCursor: null },
    ]) {
      request.mockResolvedValueOnce(page);
      expect(
        await feature.loadTemplates.execute(
          access,
          { kind: "EXPENSE", asOf: "2026-10-01", after: null },
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    for (const receipt of [
      { id, version: 2 },
      { id: actor, version: 3 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(
        await feature.saveTemplate.execute(access, operation, change(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    request.mockResolvedValueOnce({
      items: [
        { id: actor, displayName: "A" },
        { id: actor, displayName: "B" },
      ],
      nextCursor: null,
    });
    expect(
      await feature.loadAssignees.execute(
        access,
        { kind: "LEAVE", query: "", after: null },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("preserves cancellation and sanitized failure codes", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(403, {
        code: "mfa_required",
        detail: "PRIVATE TEXT",
        fields: {},
        parameters: {},
      }),
    );
    const load = createApprovalsFeature({ request }).loadTemplate;
    const result = await load.execute(access, id, null, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "mfa_required" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    const pending = new AbortController();
    pending.abort();
    expect(() => load.execute(access, id, null, pending.signal)).toThrow();
    request.mockRejectedValueOnce(new DOMException("Cancelled", "AbortError"));
    await expect(load.execute(access, id, null, signal())).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});
