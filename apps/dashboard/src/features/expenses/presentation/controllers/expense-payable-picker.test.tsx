import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { expenseIdentifier } from "../../../../../tests/fixtures/expense-claims";
import { expensePayablePage } from "../../../../../tests/fixtures/expense-payables";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toExpensePayablePage } from "../../data/mappers/expense-payable-mapper";
import type { ExpensePayablePage } from "../../domain/entities/expense-payable";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { useExpensePayablePicker } from "./use-expense-payable-picker";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const initial = { from: "2026-10-01", until: "2026-10-31", after: null };
const scope: { account: AccountId; access: CompanyAccess } = {
  account: expenseIdentifier(99) as AccountId,
  access: { companyId: company, permissions: ["expenses.pay"] },
};
const clients: QueryClient[] = [];
afterEach(() => {
  cleanup();
  for (const client of clients.splice(0)) client.clear();
});
function deferred() {
  let resolve!: (value: Result<ExpensePayablePage>) => void;
  const promise = new Promise<Result<ExpensePayablePage>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function picker(load: ExpenseUseCases["loadPayables"]) {
  const client = new QueryClient();
  clients.push(client);
  return renderHook(
    (input: typeof scope) => useExpensePayablePicker(input.account, input.access, initial, load),
    {
      initialProps: scope,
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    },
  );
}
describe("payable discovery ownership", () => {
  it.each(["account", "company", "permissions"] as const)(
    "cancels the old picker when %s changes and ignores late denial",
    async (change) => {
      const pending = deferred(),
        page = toExpensePayablePage(expensePayablePage(), company, initial),
        next = { ...scope };
      if (change === "account") next.account = expenseIdentifier(98) as AccountId;
      if (change === "company")
        next.access = { ...scope.access, companyId: expenseIdentifier(97) as CompanyId };
      if (change === "permissions")
        next.access = { ...scope.access, permissions: ["expenses.pay", "company.read"] };
      const load = {
        execute: vi
          .fn<ExpenseUseCases["loadPayables"]["execute"]>()
          .mockReturnValueOnce(pending.promise)
          .mockResolvedValue(success(page)),
      };
      const hook = picker(load);
      await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
      const old = load.execute.mock.calls[0]?.[2];
      hook.rerender(next);
      expect(old?.aborted).toBe(true);
      await waitFor(() => expect(hook.result.current.page).toBe(page));
      await act(async () => pending.resolve(failed("mfa_required")));
      expect(hook.result.current.failure).toBeNull();
      expect(hook.result.current.page).toBe(page);
    },
  );
  it("owns bounded page changes without refetching equivalent scope", async () => {
    const first = toExpensePayablePage(expensePayablePage(20), company, initial),
      second = toExpensePayablePage(expensePayablePage(2, 20), company, {
        ...initial,
        after: first.nextCursor,
      });
    const load = {
      execute: vi
        .fn<ExpenseUseCases["loadPayables"]["execute"]>()
        .mockResolvedValueOnce(success(first))
        .mockResolvedValueOnce(success(second))
        .mockResolvedValue(success(first)),
    };
    const hook = picker(load);
    await waitFor(() => expect(hook.result.current.page).toBe(first));
    hook.rerender({
      ...scope,
      access: { ...scope.access, permissions: [...scope.access.permissions] },
    });
    expect(load.execute).toHaveBeenCalledTimes(1);
    act(() => hook.result.current.next());
    await waitFor(() => expect(hook.result.current.page).toBe(second));
    expect(load.execute.mock.calls[1]?.[1].after).toBe(first.nextCursor);
    expect(hook.result.current.firstPage).toBe(false);
    act(() => hook.result.current.first());
    await waitFor(() => expect(hook.result.current.page).toBe(first));
    expect(load.execute).toHaveBeenCalledTimes(3);
  });
  it("clears data while refreshing and retains a safe failure until explicit recovery", async () => {
    const page = toExpensePayablePage(expensePayablePage(), company, initial),
      pending = deferred();
    const load = {
      execute: vi
        .fn<ExpenseUseCases["loadPayables"]["execute"]>()
        .mockResolvedValueOnce(success(page))
        .mockReturnValueOnce(pending.promise)
        .mockResolvedValue(success(page)),
    };
    const hook = picker(load);
    await waitFor(() => expect(hook.result.current.page).toBe(page));
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.loading).toBe(true));
    expect(hook.result.current.page).toBeNull();
    await act(async () => pending.resolve(failed("access_denied")));
    await waitFor(() => expect(hook.result.current.failure?.code).toBe("access_denied"));
    expect(hook.result.current.page).toBeNull();
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.page).toBe(page));
  });
  it("aborts disposed work and does not retain a late private page", async () => {
    const pending = deferred(),
      load = {
        execute: vi
          .fn<ExpenseUseCases["loadPayables"]["execute"]>()
          .mockReturnValue(pending.promise),
      };
    const hook = picker(load);
    await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
    const old = load.execute.mock.calls[0]?.[2];
    hook.unmount();
    expect(old?.aborted).toBe(true);
    await act(async () =>
      pending.resolve(success(toExpensePayablePage(expensePayablePage(), company, initial))),
    );
    await waitFor(() => expect(clients[0]?.getQueryCache().getAll()).toHaveLength(0));
  });
});
