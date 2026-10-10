import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId, CompanyId } from "../../../core/domain/identifiers";
import type { LifecycleCaseDto } from "../data/models/lifecycle-case-dto";
import { createLifecycleFeature } from "./lifecycle-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const account = "20000000-0000-4000-8000-000000000001" as AccountId;
const id = (n = 1) => `a0000000-abcd-4000-8000-${String(n).padStart(12, "0")}`;
const access = { companyId, permissions: ["people.lifecycle.read"] };
const performer = { companyId, permissions: ["people.lifecycle.perform"] };
const search = { status: "", employmentId: null, after: null };
const signal = () => new AbortController().signal;
const record = (n = 1): LifecycleCaseDto => ({
  id: id(n),
  employmentId: id(n + 100),
  employee: { id: id(n + 100), name: `Employee ${n}`, employeeNumber: `E${n}` },
  kind: "ONBOARDING",
  status: "OPEN",
  targetDate: "2026-10-01",
  templateId: id(99),
  templateVersion: 0,
  templateName: "Standard onboarding",
  version: 2,
  createdBy: account,
  createdAt: "2026-10-01T00:00:00.123456Z",
  tasks: [
    {
      key: "equipment",
      title: "Review equipment",
      required: true,
      dueDate: "2026-10-01",
      assigneeId: account,
      status: "PENDING",
      completedBy: null,
      completedAt: null,
    },
  ],
});
const assignment = (n = 1) => {
  const dto = record(n);
  return {
    caseId: dto.id,
    employmentId: dto.employmentId,
    employee: dto.employee,
    kind: dto.kind,
    caseVersion: dto.version,
    task: dto.tasks[0],
  };
};
const event = (version: number) => ({
  version,
  action: "TASK_PENDING",
  taskKey: "equipment",
  actorId: account,
  assigneeId: account,
  reason: "Recheck equipment",
  recordedAt: "2026-10-01T00:00:00Z",
});

describe("lifecycle case read boundary", () => {
  it("keeps a maximum-size checklist page within the HTTP JSON budget", async () => {
    const original = record();
    const task = original.tasks[0];
    if (!task) throw new Error("Expected task");
    const items = Array.from({ length: 10 }, (_, n) => ({
      ...record(n + 1),
      templateName: "\u0001".repeat(120),
      employee: { ...record(n + 1).employee, name: "\u0001".repeat(200) },
      tasks: Array.from({ length: 64 }, (_, index) => ({
        ...task,
        key: `t${String(index).padStart(47, "0")}`,
        title: "\u0001".repeat(160),
        status: "DONE",
        completedBy: account,
        completedAt: "2026-10-01T00:00:00.123456789Z",
      })),
    }));
    const dto = { items, nextCursor: id(10) };
    expect(new Blob([JSON.stringify(dto)]).size).toBeLessThan(1_048_576);
    const request = vi.fn<HttpClient["request"]>().mockResolvedValueOnce(dto);
    expect(
      await createLifecycleFeature({ request }).loadCases.execute(access, search, signal()),
    ).toMatchObject({ ok: true });
  });
  it("separates company reads from assigned tasks and rejects invalid filters before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLifecycleFeature({ request });
    for (const permissions of [
      [],
      ["people.read"],
      ["people.lifecycle.manage"],
      performer.permissions,
    ]) {
      expect(
        await feature.loadCases.execute({ ...access, permissions }, search, signal()),
      ).toMatchObject({ ok: false });
      expect(
        await feature.loadCase.execute({ ...access, permissions }, id(), signal()),
      ).toMatchObject({ ok: false });
      expect(
        await feature.loadHistory.execute({ ...access, permissions }, id(), null, signal()),
      ).toMatchObject({ ok: false });
    }
    expect(await feature.loadAssignedTasks.execute(access, account, null, signal())).toMatchObject({
      ok: false,
    });
    for (const invalid of [
      { ...search, status: "unknown" },
      { ...search, after: "../path" },
      { ...search, employmentId: "" },
    ])
      expect(await feature.loadCases.execute(access, invalid, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_page" },
      });
    expect(await feature.loadCase.execute(access, "../path", signal())).toMatchObject({
      ok: false,
    });
    for (const after of ["", "-1", "01", "1.0", "9007199254740992"])
      expect(await feature.loadHistory.execute(access, id(), after, signal())).toMatchObject({
        ok: false,
      });
    for (const after of ["", id(), `${id()}:UPPER`, `${id()}:task:extra`, "x".repeat(10000)])
      expect(
        await feature.loadAssignedTasks.execute(performer, account, after, signal()),
      ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
  });

  it("maps only scoped immutable case fields without loading employee or profile endpoints", async () => {
    const dto = {
      ...record(),
      id: id().toUpperCase(),
      employee: { ...record().employee, privateField: "PRIVATE" },
      tasks: [{ ...record().tasks[0], privateField: "PRIVATE" }],
      privateField: "PRIVATE",
    };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const feature = createLifecycleFeature({ request });
    const result = await feature.loadCase.execute(access, id().toUpperCase(), signal());
    if (!result.ok) throw new Error("Expected case");
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/lifecycle/cases/${id()}`,
    });
    expect(result.value).toMatchObject({
      id: id(),
      companyId,
      employee: { id: id(101), name: "Employee 1" },
    });
    for (const value of [
      result.value,
      result.value.employee,
      result.value.tasks,
      result.value.tasks[0],
    ])
      expect(Object.isFrozen(value)).toBe(true);
    expect(JSON.stringify(result.value)).not.toContain("PRIVATE");
    expect(request).toHaveBeenCalledTimes(1);
  });

  it("rejects unrelated identities, invalid task state, duplicate keys and malformed dates", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLifecycleFeature({ request });
    for (const dto of [
      record(2),
      { ...record(), employee: { ...record().employee, id: id(102) } },
      { ...record(), employee: { ...record().employee, name: " " } },
      { ...record(), tasks: [] },
      { ...record(), tasks: [...record().tasks, ...record().tasks] },
      { ...record(), targetDate: "2026-02-30" },
      { ...record(), createdAt: "2026-02-30T00:00:00Z" },
      { ...record(), tasks: [{ ...record().tasks[0], dueDate: "2026-13-01" }] },
      { ...record(), tasks: [{ ...record().tasks[0], status: "DONE" }] },
      { ...record(), tasks: [{ ...record().tasks[0], completedBy: account }] },
      {
        ...record(),
        tasks: [
          {
            ...record().tasks[0],
            status: "WAIVED",
            completedBy: account,
            completedAt: "2026-10-01T01:00:00Z",
          },
        ],
      },
      { ...record(), version: Number.MAX_SAFE_INTEGER + 1 },
    ]) {
      request.mockResolvedValueOnce(dto);
      expect(await feature.loadCase.execute(access, id(), signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("enforces bounded UUID cursor progress and the requested company-resource filters", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLifecycleFeature({ request });
    const items = Array.from({ length: 10 }, (_, n) => record(n + 1));
    request.mockResolvedValueOnce({ items, nextCursor: id(10) });
    const page = await feature.loadCases.execute(access, search, signal());
    if (!page.ok) throw new Error("Expected page");
    expect(page.value.nextCursor).toBe(id(10));
    expect(Object.isFrozen(page.value.items)).toBe(true);
    for (const dto of [
      { items: [record(), record()], nextCursor: null },
      { items: [record(2), record()], nextCursor: null },
      { items: [record()], nextCursor: id() },
      { items, nextCursor: id(19) },
      { items: [...items, record(21)], nextCursor: null },
    ]) {
      request.mockResolvedValueOnce(dto);
      expect(await feature.loadCases.execute(access, search, signal())).toMatchObject({
        ok: false,
      });
    }
    for (const filter of [
      { ...search, status: "COMPLETED" },
      { ...search, employmentId: id(102) },
      { ...search, after: id() },
    ]) {
      request.mockResolvedValueOnce({ items: [record()], nextCursor: null });
      expect(await feature.loadCases.execute(access, filter, signal())).toMatchObject({
        ok: false,
      });
    }
    request.mockResolvedValueOnce({ items: [record(21)], nextCursor: null });
    expect(
      await feature.loadCases.execute(
        access,
        { status: "OPEN", employmentId: id(121).toUpperCase(), after: id(10).toUpperCase() },
        signal(),
      ),
    ).toMatchObject({ ok: true });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/lifecycle/cases?limit=10&status=OPEN&employmentId=${id(121)}&after=${id(10)}`,
    );
  });

  it("maps only this account's pending assignments with coherent case versions and finite cursors", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLifecycleFeature({ request });
    const items = Array.from({ length: 50 }, (_, n) => assignment(n + 1));
    request.mockResolvedValueOnce({ items, nextCursor: `${id(50)}:equipment` });
    const page = await feature.loadAssignedTasks.execute(performer, account, null, signal());
    if (!page.ok) throw new Error("Expected assignments");
    expect(Object.isFrozen(page.value.items)).toBe(true);
    expect(Object.isFrozen(page.value.items[0]?.employee)).toBe(true);
    expect(page.value.items[0]).toMatchObject({
      companyId,
      caseStatus: "OPEN",
      caseVersion: 2,
      task: { assigneeId: account },
    });
    for (const dto of [
      { items: [assignment(), assignment()], nextCursor: null },
      {
        items: [
          assignment(),
          { ...assignment(), caseVersion: 3, task: { ...assignment().task, key: "access" } },
        ],
        nextCursor: null,
      },
      {
        items: [{ ...assignment(), task: { ...assignment().task, assigneeId: id(55) } }],
        nextCursor: null,
      },
      {
        items: [
          {
            ...assignment(),
            task: {
              ...assignment().task,
              status: "DONE",
              completedBy: account,
              completedAt: "2026-10-01T01:00:00Z",
            },
          },
        ],
        nextCursor: null,
      },
      { items: [assignment()], nextCursor: `${id()}:equipment` },
      { items, nextCursor: `${id(49)}:equipment` },
      { items: [...items, assignment(51)], nextCursor: null },
    ]) {
      request.mockResolvedValueOnce(dto);
      expect(
        await feature.loadAssignedTasks.execute(performer, account, null, signal()),
      ).toMatchObject({ ok: false });
    }
    request.mockResolvedValueOnce({ items: [assignment()], nextCursor: null });
    expect(
      await feature.loadAssignedTasks.execute(performer, account, `${id()}:equipment`, signal()),
    ).toMatchObject({ ok: false });
    request.mockResolvedValueOnce({ items: [assignment(51)], nextCursor: null });
    expect(
      await feature.loadAssignedTasks.execute(
        { companyId, permissions: ["people.lifecycle.manage"] },
        account,
        `${id(50).toUpperCase()}:equipment`,
        signal(),
      ),
    ).toMatchObject({ ok: true });
    expect(request.mock.lastCall?.[0].path).toContain(`after=${id(50)}%3Aequipment`);
  });

  it("keeps one increasing immutable history page and rejects stalled or malformed history", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLifecycleFeature({ request });
    const items = Array.from({ length: 50 }, (_, n) => event(n + 2));
    request.mockResolvedValueOnce({ items, nextCursor: "51" });
    const page = await feature.loadHistory.execute(access, id(), "1", signal());
    if (!page.ok) throw new Error("Expected history");
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/lifecycle/cases/${id()}/history?limit=50&after=1`,
    );
    expect(Object.isFrozen(page.value.items[0])).toBe(true);
    for (const dto of [
      { items: [event(1)], nextCursor: null },
      { items: [event(2), event(2)], nextCursor: null },
      { items: [event(3), event(2)], nextCursor: null },
      { items: [event(2)], nextCursor: "2" },
      { items, nextCursor: "50" },
      { items: [{ ...event(2), recordedAt: "invalid" }], nextCursor: null },
      { items: [{ ...event(2), reason: " " }], nextCursor: null },
    ]) {
      request.mockResolvedValueOnce(dto);
      expect(await feature.loadHistory.execute(access, id(), "1", signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("preserves failures and discards cancelled reads without retries or raw diagnostics", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValueOnce(
        new HttpResponseError(403, { code: "mfa_required", detail: "PRIVATE" }),
      )
      .mockRejectedValueOnce(new TypeError("PRIVATE"));
    const feature = createLifecycleFeature({ request });
    expect(await feature.loadCase.execute(access, id(), signal())).toMatchObject({
      ok: false,
      failure: { code: "mfa_required" },
    });
    const failed = await feature.loadAssignedTasks.execute(performer, account, null, signal());
    expect(failed).toMatchObject({ ok: false, failure: { code: "connection_unavailable" } });
    expect(JSON.stringify(failed)).not.toContain("PRIVATE");
    const owner = new AbortController();
    owner.abort();
    expect(() => feature.loadHistory.execute(access, id(), null, owner.signal)).toThrow();
    expect(request).toHaveBeenCalledTimes(2);
    let resolve!: (value: unknown) => void;
    request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const pendingOwner = new AbortController();
    const pending = feature.loadCases.execute(access, search, pendingOwner.signal);
    pendingOwner.abort();
    resolve({ items: [record()], nextCursor: null });
    await expect(pending).rejects.toMatchObject({ name: "AbortError" });
    expect(request).toHaveBeenCalledTimes(3);
  });
});
