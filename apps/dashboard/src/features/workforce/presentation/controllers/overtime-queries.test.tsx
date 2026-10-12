import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toOvertimeDetails } from "../../data/mappers/overtime-details-mapper";
import { toOvertimePage } from "../../data/mappers/overtime-page-mapper";
import {
  company,
  identifier,
  overtimeAccess,
  overtimeDetailsDtoFixture,
  overtimeDtoFixture,
  overtimeId,
  overtimeSearch,
} from "../../di/overtime-fixture";
import type { OvertimeRequestId } from "../../domain/entities/overtime-request";
import type { OvertimeSearchInput } from "../../domain/entities/overtime-search";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { useOvertimeRequest } from "./use-overtime-request";
import { useOvertimeRequests } from "./use-overtime-requests";

type Scope = {
  account: AccountId;
  access: CompanyAccess;
  search: OvertimeSearchInput;
  id: string;
  after: string | null;
};
const scope: Scope = {
  account: identifier(90) as AccountId,
  access: overtimeAccess,
  search: overtimeSearch,
  id: overtimeId,
  after: null,
};
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
const page = () =>
  toOvertimePage({ items: [overtimeDtoFixture()], nextCursor: null }, company, overtimeSearch);
const details = () =>
  toOvertimeDetails(overtimeDetailsDtoFixture(), company, overtimeId as OvertimeRequestId, null);
function renderDirectory(load: WorkforceUseCases["loadOvertimeRequests"]) {
  return renderHook(
    (input: Scope) => useOvertimeRequests(input.account, input.access, input.search, load),
    { initialProps: scope, wrapper: wrapper() },
  );
}
function renderDetails(load: WorkforceUseCases["loadOvertimeRequest"]) {
  return renderHook(
    (input: Scope) => useOvertimeRequest(input.account, input.access, input.id, input.after, load),
    { initialProps: scope, wrapper: wrapper() },
  );
}

describe("overtime read ownership", () => {
  it.each(["account", "company", "permissions", "employee", "dates", "status", "cursor"] as const)(
    "directory replacement cancels old %s results",
    async (change) => {
      const pending = deferred<ReturnType<typeof page>>(),
        current = page(),
        next = { ...scope };
      if (change === "account") next.account = identifier(91) as AccountId;
      if (change === "company")
        next.access = { ...scope.access, companyId: identifier(92) as CompanyId };
      if (change === "permissions")
        next.access = { ...scope.access, permissions: ["workforce.read"] };
      if (change === "employee") next.search = { ...scope.search, employeeId: identifier(93) };
      if (change === "dates") next.search = { ...scope.search, until: "2026-10-13" };
      if (change === "status") next.search = { ...scope.search, status: "PENDING" };
      if (change === "cursor") next.search = { ...scope.search, after: identifier(94) };
      const load = {
        execute: vi
          .fn<WorkforceUseCases["loadOvertimeRequests"]["execute"]>()
          .mockReturnValueOnce(pending.promise)
          .mockResolvedValue(success(current)),
      };
      const hook = renderDirectory(load);
      await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
      const signal = load.execute.mock.calls[0]?.[2];
      hook.rerender(next);
      expect(signal?.aborted).toBe(true);
      await waitFor(() => expect(hook.result.current.page).toBe(current));
      await act(async () => pending.resolve(success(page())));
      expect(hook.result.current.page).toBe(current);
      expect(load.execute).toHaveBeenCalledTimes(2);
    },
  );

  it.each(["account", "company", "permissions", "request", "history"] as const)(
    "detail replacement cancels old %s results",
    async (change) => {
      const pending = deferred<ReturnType<typeof details>>(),
        current = details(),
        next = { ...scope };
      if (change === "account") next.account = identifier(91) as AccountId;
      if (change === "company")
        next.access = { ...scope.access, companyId: identifier(92) as CompanyId };
      if (change === "permissions")
        next.access = { ...scope.access, permissions: ["workforce.read"] };
      if (change === "request") next.id = identifier(93);
      if (change === "history") next.after = "1";
      const load = {
        execute: vi
          .fn<WorkforceUseCases["loadOvertimeRequest"]["execute"]>()
          .mockReturnValueOnce(pending.promise)
          .mockResolvedValue(success(current)),
      };
      const hook = renderDetails(load);
      await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
      const signal = load.execute.mock.calls[0]?.[3];
      hook.rerender(next);
      expect(signal?.aborted).toBe(true);
      await waitFor(() => expect(hook.result.current.details).toBe(current));
      await act(async () => pending.resolve(success(details())));
      expect(hook.result.current.details).toBe(current);
      expect(load.execute).toHaveBeenCalledTimes(2);
    },
  );

  it("releases a disposed directory and ignores a late MFA failure", async () => {
    const pending = deferred<ReturnType<typeof page>>();
    const load = {
      execute: vi
        .fn<WorkforceUseCases["loadOvertimeRequests"]["execute"]>()
        .mockReturnValue(pending.promise),
    };
    const hook = renderDirectory(load);
    await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
    const signal = load.execute.mock.calls[0]?.[2];
    hook.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => pending.resolve(failed("mfa_required")));
    await waitFor(() => expect(clients[0]?.getQueryCache().getAll()).toHaveLength(0));
  });

  it("releases disposed details and ignores a late access failure", async () => {
    const pending = deferred<ReturnType<typeof details>>();
    const load = {
      execute: vi
        .fn<WorkforceUseCases["loadOvertimeRequest"]["execute"]>()
        .mockReturnValue(pending.promise),
    };
    const hook = renderDetails(load);
    await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
    const signal = load.execute.mock.calls[0]?.[3];
    hook.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => pending.resolve(failed("company_access_denied")));
    await waitFor(() => expect(clients[0]?.getQueryCache().getAll()).toHaveLength(0));
  });

  it("keeps prior directory rows hidden during pending and failed refresh until explicit recovery", async () => {
    const pending = deferred<ReturnType<typeof page>>(),
      current = page();
    const load = {
      execute: vi
        .fn<WorkforceUseCases["loadOvertimeRequests"]["execute"]>()
        .mockResolvedValueOnce(success(current))
        .mockReturnValueOnce(pending.promise)
        .mockResolvedValue(success(current)),
    };
    const hook = renderDirectory(load);
    await waitFor(() => expect(hook.result.current.page).toBe(current));
    hook.rerender({
      ...scope,
      access: { ...scope.access, permissions: [...scope.access.permissions] },
    });
    expect(load.execute).toHaveBeenCalledTimes(1);
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.loading).toBe(true));
    expect(hook.result.current.page).toBe(null);
    await act(async () => pending.resolve(failed("mfa_required")));
    await waitFor(() => expect(hook.result.current.failure?.code).toBe("mfa_required"));
    expect(hook.result.current.page).toBe(null);
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.page).toBe(current));
    expect(Object.isFrozen(hook.result.current.page?.items)).toBe(true);
    expect(load.execute).toHaveBeenCalledTimes(3);
  });

  it("clears the old details during refresh and retains failure until a new read succeeds", async () => {
    const pending = deferred<ReturnType<typeof details>>(),
      current = details();
    const load = {
      execute: vi
        .fn<WorkforceUseCases["loadOvertimeRequest"]["execute"]>()
        .mockResolvedValueOnce(success(current))
        .mockReturnValueOnce(pending.promise)
        .mockResolvedValue(success(current)),
    };
    const hook = renderDetails(load);
    await waitFor(() => expect(hook.result.current.details).toBe(current));
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.loading).toBe(true));
    expect(hook.result.current.details).toBe(null);
    await act(async () => pending.resolve(failed("company_access_denied")));
    await waitFor(() => expect(hook.result.current.failure?.code).toBe("company_access_denied"));
    expect(hook.result.current.details).toBe(null);
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.details).toBe(current));
    expect(Object.isFrozen(hook.result.current.details?.history.items)).toBe(true);
    expect(load.execute).toHaveBeenCalledTimes(3);
  });
});
