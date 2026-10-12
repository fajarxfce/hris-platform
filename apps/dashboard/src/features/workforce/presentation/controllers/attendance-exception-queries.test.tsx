import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  attendanceExceptionPageFixture,
  exceptionSearch,
} from "../../di/attendance-exception-fixture";
import { attendanceAccess, identifier } from "../../di/attendance-fixture";
import type { AttendanceExceptionPage } from "../../domain/entities/attendance-exception-page";
import type { AttendanceExceptionSearch } from "../../domain/entities/attendance-exception-search";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { useAttendanceExceptions } from "./use-attendance-exceptions";

type Scope = { account: AccountId; access: CompanyAccess; search: AttendanceExceptionSearch };
const scope: Scope = {
  account: identifier(90) as AccountId,
  access: attendanceAccess,
  search: exceptionSearch,
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
function deferred() {
  let resolve!: (value: Result<AttendanceExceptionPage>) => void;
  const promise = new Promise<Result<AttendanceExceptionPage>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function renderExceptions(load: WorkforceUseCases["loadAttendanceExceptions"]) {
  return renderHook(
    (input: Scope) => useAttendanceExceptions(input.account, input.access, input.search, load),
    {
      initialProps: scope,
      wrapper: wrapper(),
    },
  );
}

describe("attendance exception read lifetime", () => {
  it.each(["account", "company", "range", "filter", "cursor", "permissions"] as const)(
    "cancels obsolete %s ownership and ignores its late page",
    async (change) => {
      const old = deferred(),
        next: Scope = { ...scope };
      if (change === "account") next.account = identifier(91) as AccountId;
      if (change === "company")
        next.access = { ...scope.access, companyId: identifier(92) as CompanyId };
      if (change === "range") next.search = { ...scope.search, from: "2026-10-10" };
      if (change === "filter") next.search = { ...scope.search, kind: "PENDING" };
      if (change === "cursor")
        next.search = { ...scope.search, after: attendanceExceptionPageFixture().nextCursor };
      if (change === "permissions")
        next.access = { ...scope.access, permissions: ["workforce.read"] };
      const current = Object.freeze({
        ...attendanceExceptionPageFixture(next.search),
        companyId: next.access.companyId,
      });
      const load = {
        execute: vi
          .fn<WorkforceUseCases["loadAttendanceExceptions"]["execute"]>()
          .mockReturnValueOnce(old.promise)
          .mockResolvedValue(success(current)),
      };
      const hook = renderExceptions(load);
      await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
      const oldSignal = load.execute.mock.calls[0]?.[2];
      hook.rerender(next);
      expect(oldSignal?.aborted).toBe(true);
      await waitFor(() => expect(hook.result.current.page).toBe(current));
      await act(async () => old.resolve(success(attendanceExceptionPageFixture())));
      expect(hook.result.current.page).toBe(current);
      expect(load.execute).toHaveBeenCalledTimes(2);
    },
  );

  it("removes a disposed page and ignores its late access failure", async () => {
    const pending = deferred();
    const load = {
      execute: vi
        .fn<WorkforceUseCases["loadAttendanceExceptions"]["execute"]>()
        .mockReturnValue(pending.promise),
    };
    const hook = renderExceptions(load);
    await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
    const signal = load.execute.mock.calls[0]?.[2];
    hook.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => pending.resolve(failed("mfa_required")));
    await waitFor(() => expect(clients[0]?.getQueryCache().getAll()).toHaveLength(0));
    expect(load.execute).toHaveBeenCalledTimes(1);
  });

  it("does not reload an equivalent query and hides old rows throughout explicit recovery", async () => {
    const pending = deferred(),
      page = attendanceExceptionPageFixture();
    const load = {
      execute: vi
        .fn<WorkforceUseCases["loadAttendanceExceptions"]["execute"]>()
        .mockResolvedValueOnce(success(page))
        .mockReturnValueOnce(pending.promise)
        .mockResolvedValue(success(page)),
    };
    const hook = renderExceptions(load);
    await waitFor(() => expect(hook.result.current.page).toBe(page));
    hook.rerender({
      ...scope,
      search: { ...scope.search },
      access: { ...scope.access, permissions: [...scope.access.permissions] },
    });
    expect(load.execute).toHaveBeenCalledTimes(1);
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.loading).toBe(true));
    expect(hook.result.current.page).toBe(null);
    await act(async () => pending.resolve(failed("company_access_denied")));
    await waitFor(() => expect(hook.result.current.failure?.code).toBe("company_access_denied"));
    expect(hook.result.current.page).toBe(null);
    expect(load.execute).toHaveBeenCalledTimes(2);
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.page).toBe(page));
    expect(Object.isFrozen(hook.result.current.page?.items[0])).toBe(true);
    expect(hook.result.current.failure).toBe(null);
    expect(load.execute).toHaveBeenCalledTimes(3);
  });
});
