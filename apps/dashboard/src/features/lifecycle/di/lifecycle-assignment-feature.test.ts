import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { LifecycleTaskAssignment } from "../domain/entities/lifecycle-task-assignment";
import { createLifecycleFeature } from "./lifecycle-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["people.lifecycle.manage"] };
const id = (n = 1) => `a0000000-abcd-4000-8000-${String(n).padStart(12, "0")}`;
const operation = "30000000-0000-4000-8000-000000000001" as OperationId;
const change: LifecycleTaskAssignment = {
  caseId: id(),
  taskKey: "equipment",
  expectedVersion: 7,
  assigneeId: id(2),
  reason: "Assign equipment review",
};
const signal = () => new AbortController().signal;
const member = (n = 1) => ({ id: id(n), displayName: `Member ${n}` });

describe("lifecycle assignment boundary", () => {
  it("requires lifecycle management and bounded valid inputs before lookup or assignment I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLifecycleFeature({ request });
    for (const permissions of [
      [],
      ["identity.manage"],
      ["people.manage"],
      ["people.lifecycle.read"],
      ["people.lifecycle.perform"],
    ]) {
      expect(
        await feature.loadAssignees.execute({ companyId, permissions }, "", null, signal()),
      ).toMatchObject({ ok: false });
      expect(
        await feature.assignTask.execute({ companyId, permissions }, operation, change, signal()),
      ).toMatchObject({ ok: false });
    }
    for (const [query, after] of [
      ["x".repeat(121), null],
      ["", ""],
      ["", "../path"],
    ] as const)
      expect(await feature.loadAssignees.execute(access, query, after, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_page" },
      });
    for (const invalid of [
      { ...change, caseId: "../case" },
      { ...change, taskKey: "../key" },
      { ...change, assigneeId: "" },
      { ...change, assigneeId: "../account" },
      { ...change, expectedVersion: -1 },
      { ...change, expectedVersion: Number.MAX_SAFE_INTEGER },
      { ...change, expectedVersion: 0.5 },
      { ...change, reason: " " },
      { ...change, reason: "x".repeat(1001) },
    ])
      expect(await feature.assignTask.execute(access, operation, invalid, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_lifecycle_assignment" },
      });
    expect(
      await feature.assignTask.execute(access, "invalid" as OperationId, change, signal()),
    ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
  });

  it("maps only immutable scoped member references and encodes the literal query", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({
      items: [
        { ...member(2), id: id(2).toUpperCase(), email: "PRIVATE", permissions: ["PRIVATE"] },
      ],
      nextCursor: null,
    });
    const result = await createLifecycleFeature({ request }).loadAssignees.execute(
      access,
      " Lookup%_ ",
      id().toUpperCase(),
      signal(),
    );
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/lifecycle/assignees?limit=50&query=Lookup%25_&after=${id()}`,
    );
    if (!result.ok) throw new Error("Expected member references");
    expect(result.value.items).toEqual([{ id: id(2), companyId, displayName: "Member 2" }]);
    for (const value of [result.value, result.value.items, result.value.items[0]])
      expect(Object.isFrozen(value)).toBe(true);
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
  });

  it("rejects stalled cursors, duplicate references and malformed lookup pages", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLifecycleFeature({ request });
    const items = Array.from({ length: 50 }, (_, n) => member(n + 1));
    request.mockResolvedValueOnce({ items, nextCursor: id(50) });
    expect(await feature.loadAssignees.execute(access, "", null, signal())).toMatchObject({
      ok: true,
      value: { nextCursor: id(50) },
    });
    for (const dto of [
      { items: [member(), member()], nextCursor: null },
      { items: [member(2), member()], nextCursor: null },
      { items: [member()], nextCursor: id() },
      { items, nextCursor: id(49) },
      { items: [...items, member(51)], nextCursor: null },
      { items: [{ ...member(), displayName: " " }], nextCursor: null },
    ]) {
      request.mockResolvedValueOnce(dto);
      expect(await feature.loadAssignees.execute(access, "", null, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    request.mockResolvedValueOnce({ items: [member()], nextCursor: null });
    expect(await feature.loadAssignees.execute(access, "", id(), signal())).toMatchObject({
      ok: false,
    });
  });

  it("submits an explicit assignment or removal with a matching next-version receipt", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: id().toUpperCase(), version: 8, private: "PRIVATE" });
    const action = createLifecycleFeature({ request }).assignTask;
    const input = {
      ...change,
      caseId: id().toUpperCase(),
      assigneeId: id(2).toUpperCase(),
      taskKey: " equipment ",
      reason: " Assign equipment review ",
      displayName: "PRIVATE",
    };
    const result = await action.execute(access, operation, input, signal());
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/lifecycle/cases/${id()}/tasks/equipment/assignee`,
      method: "PUT",
      operationId: operation,
      body: { expectedVersion: 7, assigneeId: id(2), reason: "Assign equipment review" },
    });
    expect(result).toEqual({ ok: true, value: { id: id(), version: 8 } });
    await action.execute(access, operation, { ...change, assigneeId: null }, signal());
    expect(request.mock.lastCall?.[0].body).toMatchObject({ assigneeId: null });
    for (const receipt of [
      { id: id(2), version: 8 },
      { id: id(), version: 7 },
      { id: id(), version: 9 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(await action.execute(access, operation, change, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("preserves a rejected candidate without retries or raw diagnostic text", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValue(
        new HttpResponseError(422, { code: "lifecycle_assignee_unavailable", detail: "PRIVATE" }),
      );
    const result = await createLifecycleFeature({ request }).assignTask.execute(
      access,
      operation,
      change,
      signal(),
    );
    expect(result).toMatchObject({
      ok: false,
      failure: { code: "lifecycle_assignee_unavailable" },
    });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    expect(request).toHaveBeenCalledOnce();
  });

  it.each(["lookup", "assignment"] as const)(
    "cancels a pending %s and discards its late response",
    async (kind) => {
      let resolve!: (value: unknown) => void;
      const request = vi.fn<HttpClient["request"]>().mockImplementationOnce(
        () =>
          new Promise((done) => {
            resolve = done;
          }),
      );
      const feature = createLifecycleFeature({ request });
      const execute = (owner: AbortSignal) =>
        kind === "lookup"
          ? feature.loadAssignees.execute(access, "", null, owner)
          : feature.assignTask.execute(access, operation, change, owner);
      const aborted = new AbortController();
      aborted.abort();
      expect(() => execute(aborted.signal)).toThrow();
      expect(request).not.toHaveBeenCalled();
      const owner = new AbortController();
      const pending = execute(owner.signal);
      owner.abort();
      resolve(
        kind === "lookup" ? { items: [member()], nextCursor: null } : { id: id(), version: 8 },
      );
      await expect(pending).rejects.toMatchObject({ name: "AbortError" });
    },
  );
});
