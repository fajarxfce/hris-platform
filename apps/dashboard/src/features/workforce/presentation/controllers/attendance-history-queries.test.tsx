import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { attendanceCorrectionHistoryFixture } from "../../di/attendance-correction-fixture";
import {
  attendanceAccess,
  attendanceDate,
  employeeId,
  identifier,
} from "../../di/attendance-fixture";
import type { AttendanceCorrectionHistory } from "../../domain/entities/attendance-correction-history";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { useAttendanceCorrectionHistory } from "./use-attendance-correction-history";

type Scope = {
  account: AccountId;
  access: CompanyAccess;
  employee: string;
  date: string;
  after: string | null;
};
const scope: Scope = {
  account: identifier(90) as AccountId,
  access: attendanceAccess,
  employee: employeeId,
  date: attendanceDate,
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
function deferred() {
  let resolve!: (value: Result<AttendanceCorrectionHistory>) => void;
  const promise = new Promise<Result<AttendanceCorrectionHistory>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function renderHistory(load: WorkforceUseCases["loadAttendanceCorrectionHistory"]) {
  return renderHook(
    (input: Scope) =>
      useAttendanceCorrectionHistory(
        input.account,
        input.access,
        input.employee,
        input.date,
        input.after,
        load,
      ),
    { initialProps: scope, wrapper: wrapper() },
  );
}

describe("attendance correction history read ownership", () => {
  it.each(["account", "company", "employee", "date", "cursor", "permissions"] as const)(
    "cancels old %s ownership and discards its late result",
    async (change) => {
      const old = deferred();
      const current = attendanceCorrectionHistoryFixture("3");
      const load = {
        execute: vi
          .fn<WorkforceUseCases["loadAttendanceCorrectionHistory"]["execute"]>()
          .mockReturnValueOnce(old.promise)
          .mockResolvedValue(success(current)),
      };
      const hook = renderHistory(load);
      await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
      const oldSignal = load.execute.mock.calls[0]?.[4];
      const next = { ...scope };
      if (change === "account") next.account = identifier(91) as AccountId;
      if (change === "company")
        next.access = { ...scope.access, companyId: identifier(92) as CompanyId };
      if (change === "employee") next.employee = identifier(93);
      if (change === "date") next.date = "2026-10-11";
      if (change === "cursor") next.after = "3";
      if (change === "permissions")
        next.access = { ...scope.access, permissions: ["workforce.read"] };
      hook.rerender(next);
      expect(oldSignal?.aborted).toBe(true);
      await waitFor(() => expect(hook.result.current.history).toBe(current));
      await act(async () => old.resolve(success(attendanceCorrectionHistoryFixture())));
      expect(hook.result.current.history).toBe(current);
      expect(load.execute).toHaveBeenCalledTimes(2);
    },
  );

  it("releases a disposed page and ignores a late access failure", async () => {
    const pending = deferred();
    const load = {
      execute: vi
        .fn<WorkforceUseCases["loadAttendanceCorrectionHistory"]["execute"]>()
        .mockReturnValue(pending.promise),
    };
    const hook = renderHistory(load);
    await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
    const signal = load.execute.mock.calls[0]?.[4];
    hook.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => pending.resolve(failed("mfa_required")));
    await waitFor(() => expect(clients[0]?.getQueryCache().getAll()).toHaveLength(0));
    expect(load.execute).toHaveBeenCalledTimes(1);
  });

  it("hides old rows during refresh, keeps failures explicit and preserves immutable entities", async () => {
    const pending = deferred(),
      history = attendanceCorrectionHistoryFixture();
    const load = {
      execute: vi
        .fn<WorkforceUseCases["loadAttendanceCorrectionHistory"]["execute"]>()
        .mockResolvedValueOnce(success(history))
        .mockReturnValueOnce(pending.promise)
        .mockResolvedValue(success(history)),
    };
    const hook = renderHistory(load);
    await waitFor(() => expect(hook.result.current.history).toBe(history));
    hook.rerender({
      ...scope,
      access: { ...scope.access, permissions: [...scope.access.permissions] },
    });
    expect(load.execute).toHaveBeenCalledTimes(1);
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.loading).toBe(true));
    expect(hook.result.current.history).toBe(null);
    await act(async () => pending.resolve(failed("company_access_denied")));
    await waitFor(() => expect(hook.result.current.failure?.code).toBe("company_access_denied"));
    expect(hook.result.current.history).toBe(null);
    expect(load.execute).toHaveBeenCalledTimes(2);
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.history).toBe(history));
    expect(Object.isFrozen(hook.result.current.history?.items[0])).toBe(true);
    expect(hook.result.current.failure).toBe(null);
    expect(load.execute).toHaveBeenCalledTimes(3);
  });
});
