import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { LifecycleTemplateDto } from "../data/models/lifecycle-template-dto";
import type { LifecycleTemplateChange } from "../domain/entities/lifecycle-template-change";
import { createLifecycleFeature } from "./lifecycle-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["people.lifecycle.read"] };
const manager = { companyId, permissions: ["people.lifecycle.manage"] };
const id = (n = 1) => `90000000-abcd-4000-8000-${String(n).padStart(12, "0")}`;
const signal = () => new AbortController().signal;
const template = (n = 1): LifecycleTemplateDto => ({
  id: id(n),
  code: `TEMPLATE_${String(n).padStart(3, "0")}`,
  name: `Checklist ${n}`,
  kind: "ONBOARDING",
  active: true,
  version: 2,
  tasks: [{ key: "equipment", title: "Review equipment", required: true, dueDays: -1 }],
});
const input = (): LifecycleTemplateChange => ({
  ...template(),
  expectedVersion: 2,
  reason: "Update checklist",
});

describe("lifecycle template feature boundary", () => {
  it("separates read and manage grants and rejects invalid identifiers and cursors before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLifecycleFeature({ request });
    for (const permissions of [
      [],
      ["people.lifecycle.perform"],
      ["people.read"],
      manager.permissions,
    ]) {
      expect(
        await feature.loadTemplates.execute({ ...access, permissions }, null, signal()),
      ).toMatchObject({ ok: false });
      expect(
        await feature.loadTemplate.execute({ ...access, permissions }, id(), signal()),
      ).toMatchObject({ ok: false });
    }
    for (const cursor of ["", "A", "../path", "A".repeat(33)])
      expect(await feature.loadTemplates.execute(access, cursor, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_page" },
      });
    expect(await feature.loadTemplate.execute(access, "../path", signal())).toMatchObject({
      ok: false,
    });
    expect(
      await feature.saveTemplate.execute(access, id(9) as OperationId, input(), signal()),
    ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
  });

  it("maps only scoped immutable fields and preserves a bounded database cursor", async () => {
    const dto = {
      ...template(),
      id: id().toUpperCase(),
      privateField: "PRIVATE",
      tasks: [{ ...template().tasks[0], privateField: "PRIVATE" }],
    };
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce({ items: [dto], nextCursor: null })
      .mockResolvedValueOnce(dto);
    const feature = createLifecycleFeature({ request });
    const page = await feature.loadTemplates.execute(access, "OLDER", signal());
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/lifecycle/templates?limit=20&after=OLDER`,
    );
    const details = await feature.loadTemplate.execute(access, id().toUpperCase(), signal());
    if (!page.ok || !details.ok) throw new Error("Expected template");
    expect(details.value).toMatchObject({ companyId, id: id() });
    for (const value of [
      page.value,
      page.value.items,
      page.value.items[0],
      details.value,
      details.value.tasks,
      details.value.tasks[0],
    ])
      expect(Object.isFrozen(value)).toBe(true);
    expect(JSON.stringify([page, details])).not.toContain("PRIVATE");
  });

  it("rejects malformed definitions, unrelated detail IDs, and non-progressing or oversized pages", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLifecycleFeature({ request });
    for (const dto of [
      template(2),
      { ...template(), tasks: [] },
      { ...template(), tasks: [...template().tasks, ...template().tasks] },
      { ...template(), name: " " },
      { ...template(), code: "bad code" },
      { ...template(), tasks: [{ ...template().tasks[0], dueDays: 366 }] },
      { ...template(), tasks: [{ ...template().tasks[0], title: " " }] },
      { ...template(), version: Number.MAX_SAFE_INTEGER + 1 },
    ]) {
      request.mockResolvedValueOnce(dto);
      expect(await feature.loadTemplate.execute(access, id(), signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    const items = Array.from({ length: 20 }, (_, i) => template(i + 1));
    request.mockResolvedValueOnce({ items, nextCursor: "TEMPLATE_020" });
    expect(await feature.loadTemplates.execute(access, null, signal())).toMatchObject({ ok: true });
    for (const page of [
      { items: [template(), template()], nextCursor: null },
      { items: [template(), { ...template(2), code: template().code }], nextCursor: null },
      { items: [template()], nextCursor: template().code },
      { items, nextCursor: "TEMPLATE_019" },
      { items: [...items, template(21)], nextCursor: null },
    ]) {
      request.mockResolvedValueOnce(page);
      expect(await feature.loadTemplates.execute(access, null, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    request.mockResolvedValueOnce({ items: [template()], nextCursor: null });
    expect(await feature.loadTemplates.execute(access, template().code, signal())).toMatchObject({
      ok: false,
    });
    request.mockResolvedValueOnce({
      items: [
        { ...template(), code: "UNIT_A" },
        { ...template(2), code: "UNIT-B" },
      ],
      nextCursor: null,
    });
    expect(await feature.loadTemplates.execute(access, null, signal())).toMatchObject({ ok: true });
  });

  it("validates bounded checklist commands and normalizes a whitelisted DTO for manage-only creation", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: id().toUpperCase(), version: 0 });
    const feature = createLifecycleFeature({ request });
    for (const change of [
      { ...input(), id: "../path" },
      { ...input(), code: "A" },
      { ...input(), name: " " },
      { ...input(), reason: " " },
      { ...input(), expectedVersion: Number.MAX_SAFE_INTEGER },
      { ...input(), expectedVersion: -1 },
      { ...input(), tasks: [] },
      {
        ...input(),
        tasks: Array.from({ length: 65 }, (_, i) => ({
          key: `task_${i}`,
          title: "Work",
          required: true,
          dueDays: 0,
        })),
      },
      { ...input(), tasks: [...input().tasks, ...input().tasks] },
      { ...input(), tasks: [{ key: "UPPER", title: "Work", required: true, dueDays: 0 }] },
      { ...input(), tasks: [{ key: "work", title: "Work", required: true, dueDays: Number.NaN }] },
      { ...input(), tasks: [{ key: "work", title: "Work", required: true, dueDays: -91 }] },
    ])
      expect(
        await feature.saveTemplate.execute(manager, id(99) as OperationId, change, signal()),
      ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
    const change = {
      ...input(),
      id: id().toUpperCase(),
      code: " onboarding ",
      name: " Onboarding ",
      expectedVersion: null,
      reason: " New checklist ",
      privateField: "PRIVATE",
      tasks: [{ key: " equipment ", title: " Review equipment ", required: false, dueDays: -2 }],
    };
    expect(
      await feature.saveTemplate.execute(manager, id(99) as OperationId, change, signal()),
    ).toEqual({ ok: true, value: { id: id(), version: 0 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/lifecycle/templates/${id()}`,
      method: "PUT",
      operationId: id(99),
      body: {
        expectedVersion: null,
        code: "ONBOARDING",
        name: "Onboarding",
        kind: "ONBOARDING",
        active: true,
        reason: "New checklist",
        tasks: [{ key: "equipment", title: "Review equipment", required: false, dueDays: -2 }],
      },
    });
  });

  it("checks receipt identity and version inside the failure boundary", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLifecycleFeature({ request });
    for (const receipt of [
      { id: id(2), version: 3 },
      { id: id(), version: 2 },
      { id: id(), version: 4 },
      { id: id(), version: -1 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(
        await feature.saveTemplate.execute(manager, id(99) as OperationId, input(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    request.mockResolvedValueOnce({ id: id(), version: 3 });
    expect(
      await feature.saveTemplate.execute(manager, id(99) as OperationId, input(), signal()),
    ).toEqual({ ok: true, value: { id: id(), version: 3 } });
  });

  it("preserves localizable failures and cancellation without retries or diagnostic leakage", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValueOnce(
        new HttpResponseError(403, {
          code: "mfa_required",
          correlationId: id(9),
          detail: "PRIVATE",
        }),
      )
      .mockRejectedValueOnce(new TypeError("PRIVATE NETWORK FAILURE"));
    const feature = createLifecycleFeature({ request });
    const result = await feature.loadTemplate.execute(access, id(), signal());
    expect(result).toMatchObject({
      ok: false,
      failure: { code: "mfa_required", correlationId: id(9) },
    });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    expect(
      await feature.saveTemplate.execute(manager, id(99) as OperationId, input(), signal()),
    ).toMatchObject({ ok: false, failure: { code: "connection_unavailable" } });
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() => feature.loadTemplates.execute(access, null, cancelled.signal)).toThrow();
    expect(request).toHaveBeenCalledTimes(2);
    let finish!: (value: unknown) => void;
    request.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const owner = new AbortController();
    const late = feature.loadTemplate.execute(access, id(), owner.signal);
    owner.abort();
    finish(template());
    await expect(late).rejects.toMatchObject({ name: "AbortError" });
    expect(request).toHaveBeenCalledTimes(3);
  });
});
