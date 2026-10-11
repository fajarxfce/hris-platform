import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  access,
  calendarFixture,
  company,
  employeeId,
  identifier,
  calendarRangeFixture as range,
  shiftFixture,
} from "../../di/calendar-fixture";
import type { CalendarReview } from "../../domain/entities/calendar-review";
import type { WorkforceEmployeePage } from "../../domain/entities/workforce-employee-page";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { useCalendarReview } from "./use-calendar-review";
import { useShiftCatalog } from "./use-shift-catalog";
import { useWorkforceEmployees } from "./use-workforce-employees";

const account = identifier(90) as AccountId;
const other = identifier(91) as CompanyId;
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
describe("calendar read lifetimes", () => {
  it("cancels an old company read and ignores its late result", async () => {
    let release!: (result: Result<CalendarReview>) => void;
    const pending = new Promise<Result<CalendarReview>>((done) => {
      release = done;
    });
    const load = {
      execute: vi
        .fn<WorkforceUseCases["loadCalendarReview"]["execute"]>()
        .mockReturnValueOnce(pending)
        .mockResolvedValue(
          success({
            ...calendarFixture(),
            employee: {
              ...calendarFixture().employee,
              companyId: other,
              legalName: "Other company",
            },
          }),
        ),
    };
    const hook = renderHook(
      ({ companyId }) =>
        useCalendarReview(account, { ...access, companyId }, employeeId, range, load),
      { wrapper: wrapper(), initialProps: { companyId: company } },
    );
    await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
    const signal = load.execute.mock.calls[0]?.[3];
    hook.rerender({ companyId: other });
    expect(signal?.aborted).toBe(true);
    await waitFor(() =>
      expect(hook.result.current.review?.employee.legalName).toBe("Other company"),
    );
    await act(async () => release(success(calendarFixture())));
    expect(hook.result.current.review?.employee.companyId).toBe(other);
  });
  it("bounds directory observation and cancels pending work on disposal", async () => {
    let release!: (result: Result<WorkforceEmployeePage>) => void;
    const load = {
      execute: vi.fn<WorkforceUseCases["loadWorkforceEmployees"]["execute"]>().mockImplementation(
        () =>
          new Promise((done) => {
            release = done;
          }),
      ),
    };
    const hook = renderHook(
      () =>
        useWorkforceEmployees(account, access, { asOf: range.from, query: "", after: null }, load),
      { wrapper: wrapper() },
    );
    await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
    const signal = load.execute.mock.calls[0]?.[2];
    hook.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => release(failed("mfa_required")));
    expect(load.execute).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(clients[0]?.getQueryCache().getAll()).toHaveLength(0));
  });
  it("does not retry read failures or start a disabled catalog and supports explicit recovery", async () => {
    const load = {
      execute: vi
        .fn<WorkforceUseCases["loadShifts"]["execute"]>()
        .mockResolvedValueOnce(failed("connection_unavailable"))
        .mockResolvedValue(success({ items: [shiftFixture()], nextCursor: null })),
    };
    const hook = renderHook(({ enabled }) => useShiftCatalog(account, access, load, enabled), {
      wrapper: wrapper(),
      initialProps: { enabled: false },
    });
    expect(load.execute).not.toHaveBeenCalled();
    hook.rerender({ enabled: true });
    await waitFor(() => expect(hook.result.current.failure?.code).toBe("connection_unavailable"));
    expect(load.execute).toHaveBeenCalledTimes(1);
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.hasActiveShifts).toBe(true));
    expect(load.execute).toHaveBeenCalledTimes(2);
  });
});
