import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import { toWorkPeriodSnapshot } from "../../data/mappers/work-period-snapshot-mapper";
import {
  company,
  employeeId,
  identifier,
  periodAccess,
  periodJobId,
  periodMonth,
  workPeriodSnapshotDtoFixture,
} from "../../di/work-period-fixture";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { useWorkPeriodEmployees } from "./use-work-period-employees";
import { useWorkPeriodSnapshot } from "./use-work-period-snapshot";

type Scope = {
  account: AccountId;
  access: CompanyAccess;
  month: string;
  job: string;
  employee: string;
};
const scope: Scope = {
  account: identifier(90) as AccountId,
  access: periodAccess,
  month: periodMonth,
  job: periodJobId,
  employee: employeeId,
};
const snapshot = () =>
  toWorkPeriodSnapshot(
    workPeriodSnapshotDtoFixture(),
    company,
    periodMonth,
    employeeId as EmployeeId,
  );
const page = () => ({ items: [snapshot().employee], nextCursor: null, bulkRosterSupported: false });
const clients: QueryClient[] = [];
afterEach(() => {
  cleanup();
  for (const client of clients.splice(0)) client.clear();
});
function wrapper() {
  const client = new QueryClient();
  clients.push(client);
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
}
function deferred<T>() {
  let resolve!: (value: Result<T>) => void;
  const promise = new Promise<Result<T>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function renderDirectory(load: WorkforceUseCases["loadWorkPeriodEmployees"]) {
  return renderHook(
    (input: Scope) =>
      useWorkPeriodEmployees(input.account, input.access, input.month, input.job, load),
    { initialProps: scope, wrapper: wrapper() },
  );
}
function renderSnapshot(load: WorkforceUseCases["loadWorkPeriodSnapshot"]) {
  return renderHook(
    (input: Scope) =>
      useWorkPeriodSnapshot(input.account, input.access, input.month, input.employee, load),
    { initialProps: scope, wrapper: wrapper() },
  );
}
function changedScope(
  change: "account" | "company" | "permissions" | "month" | "job" | "employee",
): Scope {
  if (change === "account") return { ...scope, account: identifier(91) as AccountId };
  if (change === "company")
    return { ...scope, access: { ...scope.access, companyId: identifier(92) as CompanyId } };
  if (change === "permissions")
    return { ...scope, access: { ...scope.access, permissions: ["workforce.read"] } };
  if (change === "month") return { ...scope, month: "2026-08" };
  if (change === "job") return { ...scope, job: identifier(93) };
  return { ...scope, employee: identifier(94) };
}

describe("published work period read ownership", () => {
  it.each(["account", "company", "permissions", "month", "job"] as const)(
    "replaces %s directory scope and cancels obsolete results",
    async (change) => {
      const held = deferred<ReturnType<typeof page>>(),
        current = page();
      const load = {
        execute: vi
          .fn<WorkforceUseCases["loadWorkPeriodEmployees"]["execute"]>()
          .mockReturnValueOnce(held.promise)
          .mockResolvedValue(success(current)),
      };
      const hook = renderDirectory(load);
      await waitFor(() => expect(load.execute).toHaveBeenCalledOnce());
      const signal = load.execute.mock.lastCall?.[2];
      hook.rerender(changedScope(change));
      expect(signal?.aborted).toBe(true);
      await waitFor(() => expect(hook.result.current.page).toBe(current));
      await act(async () => held.resolve(failed("mfa_required")));
      expect(hook.result.current.page).toBe(current);
      expect(hook.result.current.failure).toBeNull();
    },
  );
  it.each(["account", "company", "permissions", "month", "employee"] as const)(
    "replaces %s snapshot scope and cancels obsolete results",
    async (change) => {
      const held = deferred<ReturnType<typeof snapshot>>(),
        current = snapshot();
      const load = {
        execute: vi
          .fn<WorkforceUseCases["loadWorkPeriodSnapshot"]["execute"]>()
          .mockReturnValueOnce(held.promise)
          .mockResolvedValue(success(current)),
      };
      const hook = renderSnapshot(load);
      await waitFor(() => expect(load.execute).toHaveBeenCalledOnce());
      const signal = load.execute.mock.lastCall?.[3];
      hook.rerender(changedScope(change));
      expect(signal?.aborted).toBe(true);
      await waitFor(() => expect(hook.result.current.snapshot).toBe(current));
      await act(async () => held.resolve(success(snapshot())));
      expect(hook.result.current.snapshot).toBe(current);
    },
  );

  it("keeps denied employee lists hidden throughout pending and failed retries", async () => {
    const held = deferred<ReturnType<typeof page>>();
    const load = {
      execute: vi
        .fn<WorkforceUseCases["loadWorkPeriodEmployees"]["execute"]>()
        .mockResolvedValueOnce(success(page()))
        .mockResolvedValueOnce(failed("access_denied"))
        .mockReturnValueOnce(held.promise),
    };
    const hook = renderDirectory(load);
    await waitFor(() => expect(hook.result.current.page).not.toBeNull());
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.failure?.code).toBe("access_denied"));
    expect(hook.result.current.page).toBeNull();
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.loading).toBe(true));
    expect(hook.result.current.page).toBeNull();
    await act(async () => held.resolve(failed("connection_unavailable")));
    await waitFor(() => expect(hook.result.current.failure?.code).toBe("connection_unavailable"));
    expect(hook.result.current.page).toBeNull();
  });

  it("keeps denied snapshots hidden and never restores stale names during a retry", async () => {
    const held = deferred<ReturnType<typeof snapshot>>();
    const load = {
      execute: vi
        .fn<WorkforceUseCases["loadWorkPeriodSnapshot"]["execute"]>()
        .mockResolvedValueOnce(success(snapshot()))
        .mockResolvedValueOnce(failed("mfa_required"))
        .mockReturnValueOnce(held.promise),
    };
    const hook = renderSnapshot(load);
    await waitFor(() => expect(hook.result.current.snapshot).not.toBeNull());
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.failure?.code).toBe("mfa_required"));
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.loading).toBe(true));
    expect(hook.result.current.snapshot).toBeNull();
    await act(async () => held.resolve(failed("connection_unavailable")));
    await waitFor(() => expect(hook.result.current.loading).toBe(false));
    expect(hook.result.current.snapshot).toBeNull();
  });

  it("releases disposed directories, snapshots and query caches", async () => {
    const list = deferred<ReturnType<typeof page>>(),
      detail = deferred<ReturnType<typeof snapshot>>();
    const loadList = {
      execute: vi
        .fn<WorkforceUseCases["loadWorkPeriodEmployees"]["execute"]>()
        .mockReturnValue(list.promise),
    };
    const loadDetail = {
      execute: vi
        .fn<WorkforceUseCases["loadWorkPeriodSnapshot"]["execute"]>()
        .mockReturnValue(detail.promise),
    };
    const directory = renderDirectory(loadList),
      result = renderSnapshot(loadDetail);
    await waitFor(() => expect(loadDetail.execute).toHaveBeenCalledOnce());
    const listSignal = loadList.execute.mock.lastCall?.[2],
      detailSignal = loadDetail.execute.mock.lastCall?.[3];
    directory.unmount();
    result.unmount();
    expect(listSignal?.aborted).toBe(true);
    expect(detailSignal?.aborted).toBe(true);
    await act(async () => {
      list.resolve(success(page()));
      detail.resolve(failed("mfa_required"));
    });
    await waitFor(() => {
      for (const client of clients) expect(client.getQueryCache().getAll()).toHaveLength(0);
    });
  });
});
