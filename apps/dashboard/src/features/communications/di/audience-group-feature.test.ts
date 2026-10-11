import { describe, expect, it, vi } from "vitest";
import type { HttpRequest } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { OperationId } from "../../../core/domain/identifiers";
import type { AudienceGroupChange } from "../domain/entities/audience-group-change";
import { employeeId, groupDetail, groupId } from "./audience-group-fixture";
import { createCommunicationsFeature } from "./communications-feature";
import { access, companyId } from "./communications-fixture";

const operation = "11000000-0000-4000-8000-000000000009" as OperationId;
const signal = () => new AbortController().signal;
const input: AudienceGroupChange = {
  id: groupId.toUpperCase(),
  expectedVersion: null,
  name: " Field team ",
  active: true,
  employmentIds: [employeeId(2).toUpperCase(), employeeId(1)],
  reason: " Initial group ",
};
describe("audience group API boundary", () => {
  it("normalizes and scopes the immutable command and validates its receipt", async () => {
    const request = vi.fn(async (_request: HttpRequest) => ({ id: groupId, version: 0 }));
    const result = await createCommunicationsFeature({ request }).saveGroup.execute(
      access,
      operation,
      input,
      signal(),
    );
    expect(result).toEqual({ ok: true, value: { id: groupId, version: 0 } });
    expect(request.mock.calls[0]?.[0]).toMatchObject({
      method: "PUT",
      operationId: operation,
      path: `/api/v1/companies/${companyId}/communications/audience-groups/${groupId}`,
      body: {
        expectedVersion: null,
        name: "Field team",
        active: true,
        employmentIds: [employeeId(1), employeeId(2)],
        reason: "Initial group",
      },
    });
    expect(input.employmentIds).toEqual([employeeId(2).toUpperCase(), employeeId(1)]);
    if (result.ok) expect(Object.isFrozen(result.value)).toBe(true);
  });
  it.each([
    { name: "" },
    { name: "x".repeat(121) },
    { name: "team\n" + "name" },
    { employmentIds: [employeeId(1), employeeId(1).toUpperCase()] },
    { employmentIds: ["../employee"] },
    { employmentIds: Array.from({ length: 5001 }, (_, index) => employeeId(index + 1)) },
    { expectedVersion: -1 },
    { expectedVersion: 1000 },
    { reason: "" },
  ])("rejects invalid proposals before I/O", async (change) => {
    const request = vi.fn();
    expect(
      await createCommunicationsFeature({ request }).saveGroup.execute(
        access,
        operation,
        { ...input, ...change },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_audience_group" } });
    expect(request).not.toHaveBeenCalled();
  });
  it("allows an empty definition and the exact bounded member count", async () => {
    const request = vi.fn(async () => ({ id: groupId, version: 0 }));
    const save = createCommunicationsFeature({ request }).saveGroup;
    expect(
      await save.execute(access, operation, { ...input, employmentIds: [] }, signal()),
    ).toMatchObject({ ok: true });
    expect(
      await save.execute(
        access,
        operation,
        {
          ...input,
          employmentIds: Array.from({ length: 5000 }, (_, index) => employeeId(index + 1)),
        },
        signal(),
      ),
    ).toMatchObject({ ok: true });
  });
  it("requires management access for all operations and validates route input before I/O", async () => {
    const request = vi.fn();
    const feature = createCommunicationsFeature({ request });
    const denied = { ...access, permissions: ["announcements.read"] };
    expect(await feature.loadGroups.execute(denied, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "access_denied" },
    });
    expect(await feature.loadGroup.execute(denied, groupId, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "access_denied" },
    });
    expect(await feature.saveGroup.execute(denied, operation, input, signal())).toMatchObject({
      ok: false,
      failure: { code: "access_denied" },
    });
    expect(await feature.loadGroups.execute(access, "bad", signal())).toMatchObject({ ok: false });
    expect(await feature.loadGroup.execute(access, "../bad", null, signal())).toMatchObject({
      ok: false,
    });
    for (const revision of ["-1", "01", "1.5", "1000"])
      expect(await feature.loadGroup.execute(access, groupId, revision, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_revision" },
      });
    expect(request).not.toHaveBeenCalled();
  });
  it("loads a precise immutable revision without acquiring employee records", async () => {
    const dto = groupDetail(3);
    const request = vi.fn(async (_request: HttpRequest) => dto);
    const result = await createCommunicationsFeature({ request }).loadGroup.execute(
      access,
      groupId,
      "3",
      signal(),
    );
    expect(request).toHaveBeenCalledTimes(1);
    expect(request.mock.calls[0]?.[0].path).toBe(
      `/api/v1/companies/${companyId}/communications/audience-groups/${groupId}/revisions/3`,
    );
    dto.employmentIds.push(employeeId(2));
    expect(result).toMatchObject({
      ok: true,
      value: { version: 3, memberCount: 1, employmentIds: [employeeId(1)] },
    });
    if (result.ok) expect(Object.isFrozen(result.value.employmentIds)).toBe(true);
  });
  it.each([
    { ...groupDetail(), id: employeeId(1) },
    { ...groupDetail(), version: 2 },
    { ...groupDetail(), employmentIds: [employeeId(1), employeeId(1)] },
    {
      ...groupDetail(),
      employmentIds: Array.from({ length: 5001 }, (_, index) => employeeId(index + 1)),
    },
  ])("rejects unrelated, oversized or incompatible detail data", async (dto) => {
    const result = await createCommunicationsFeature({
      request: async () => dto,
    }).loadGroup.execute(access, groupId, "0", signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("validates finite ascending pages and clears invalid continuations", async () => {
    const rows = Array.from({ length: 50 }, (_, index) => ({
      ...groupDetail(),
      id: `61000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
      memberCount: 1,
    }));
    let response = { items: rows, nextCursor: rows.at(-1)?.id ?? null };
    const request = vi.fn(async (_request: HttpRequest) => response);
    const list = createCommunicationsFeature({ request }).loadGroups;
    const result = await list.execute(access, null, signal());
    expect(result).toMatchObject({ ok: true, value: { nextCursor: rows.at(-1)?.id } });
    expect(
      new URL(request.mock.calls[0]?.[0].path ?? "", "https://example.invalid").searchParams.get(
        "limit",
      ),
    ).toBe("50");
    response = { items: rows.slice(0, 2), nextCursor: rows[1]?.id ?? null };
    expect(await list.execute(access, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
    const first = rows[0];
    if (!first) throw new Error("Missing fixture row");
    response = { items: [first, first], nextCursor: null };
    expect(await list.execute(access, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
    response = { items: rows, nextCursor: null };
    expect(await list.execute(access, groupId, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("preserves failure codes and rejects mismatched receipts", async () => {
    const failed = createCommunicationsFeature({
      request: async () => {
        throw new HttpResponseError(409, { code: "stale_version" });
      },
    });
    expect(await failed.saveGroup.execute(access, operation, input, signal())).toMatchObject({
      ok: false,
      failure: { code: "stale_version" },
    });
    for (const receipt of [
      { id: employeeId(1), version: 0 },
      { id: groupId, version: 1 },
    ]) {
      expect(
        await createCommunicationsFeature({ request: async () => receipt }).saveGroup.execute(
          access,
          operation,
          input,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });
  it("propagates pending cancellation and cannot return a late successful mapping", async () => {
    let release!: (value: unknown) => void;
    const waiting = new Promise<unknown>((resolve) => {
      release = resolve;
    });
    const controller = new AbortController();
    const result = createCommunicationsFeature({ request: async () => waiting }).loadGroup.execute(
      access,
      groupId,
      null,
      controller.signal,
    );
    controller.abort();
    release(groupDetail());
    await expect(result).rejects.toMatchObject({ name: "AbortError" });
  });
});
