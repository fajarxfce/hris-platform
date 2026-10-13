import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { expenseIdentifier } from "../../../../../tests/fixtures/expense-claims";
import {
  expenseDraftEmployees,
  expenseDraftReceipts,
} from "../../../../../tests/fixtures/expense-drafts";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  toExpenseDraftDirectory,
  toExpenseDraftReceiptPage,
} from "../../data/mappers/expense-drafting-mapper";
import type { ExpenseDraftDirectory } from "../../domain/entities/expense-draft-employee";
import type { ExpenseDraftReceiptPage } from "../../domain/entities/expense-draft-receipt-option";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { useExpenseDraftEmployees } from "./use-expense-draft-employees";
import { useExpenseDraftOptions } from "./use-expense-draft-options";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId,
  scope = {
    account: expenseIdentifier(1) as AccountId,
    access: { companyId: company, permissions: ["expenses.manage"] },
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
  let resolve!: (result: Result<T>) => void;
  const promise = new Promise<Result<T>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
describe("draft picker request ownership", () => {
  it.each(["account", "company", "permissions"] as const)(
    "cancels employee discovery on %s change and ignores a late denial",
    async (change) => {
      const pending = deferred<ExpenseDraftDirectory>(),
        directory = toExpenseDraftDirectory(expenseDraftEmployees(), company, null);
      const load = {
        execute: vi
          .fn<ExpenseUseCases["loadDraftEmployees"]["execute"]>()
          .mockReturnValueOnce(pending.promise)
          .mockResolvedValue(success(directory)),
      };
      const hook = renderHook(
        (input: typeof scope) => useExpenseDraftEmployees(input.account, input.access, load),
        { initialProps: scope, wrapper: wrapper() },
      );
      await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
      const old = load.execute.mock.calls[0]?.[3],
        next = { ...scope };
      if (change === "account") next.account = expenseIdentifier(2) as AccountId;
      if (change === "company")
        next.access = { ...scope.access, companyId: expenseIdentifier(2) as CompanyId };
      if (change === "permissions")
        next.access = { ...scope.access, permissions: ["expenses.manage", "company.read"] };
      hook.rerender(next);
      expect(old?.aborted).toBe(true);
      await waitFor(() => expect(hook.result.current.directory).toBe(directory));
      await act(async () => pending.resolve(failed("expense_access_denied")));
      expect(hook.result.current.failure).toBeNull();
      expect(hook.result.current.directory).toBe(directory);
    },
  );
  it("hides previous results while refreshing, never retries a denial automatically, and disposes pending reads", async () => {
    const directory = toExpenseDraftDirectory(expenseDraftEmployees(), company, null),
      pending = deferred<ExpenseDraftDirectory>();
    const load = {
      execute: vi
        .fn<ExpenseUseCases["loadDraftEmployees"]["execute"]>()
        .mockResolvedValueOnce(success(directory))
        .mockReturnValueOnce(pending.promise)
        .mockResolvedValue(failed("access_denied")),
    };
    const hook = renderHook(() => useExpenseDraftEmployees(scope.account, scope.access, load), {
      wrapper: wrapper(),
    });
    await waitFor(() => expect(hook.result.current.directory).toBe(directory));
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.directory).toBeNull());
    await act(async () => pending.resolve(failed("access_denied")));
    await waitFor(() => expect(hook.result.current.failure?.code).toBe("access_denied"));
    expect(load.execute).toHaveBeenCalledTimes(2);
    const later = deferred<ExpenseDraftDirectory>();
    load.execute.mockReturnValueOnce(later.promise);
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(3));
    const old = load.execute.mock.calls[2]?.[3];
    hook.unmount();
    expect(old?.aborted).toBe(true);
    later.resolve(success(directory));
  });
  it("keeps employee pagination bounded and does not refetch an equivalent scope", async () => {
    const first = toExpenseDraftDirectory(expenseDraftEmployees(0, 20), company, null),
      second = toExpenseDraftDirectory(expenseDraftEmployees(0, 2, 20), company, "EMP-020");
    const load = {
      execute: vi
        .fn<ExpenseUseCases["loadDraftEmployees"]["execute"]>()
        .mockResolvedValueOnce(success(first))
        .mockResolvedValueOnce(success(second)),
    };
    const hook = renderHook(
      (input: typeof scope) => useExpenseDraftEmployees(input.account, input.access, load),
      { initialProps: scope, wrapper: wrapper() },
    );
    await waitFor(() => expect(hook.result.current.directory).toBe(first));
    hook.rerender({
      ...scope,
      access: { ...scope.access, permissions: [...scope.access.permissions] },
    });
    expect(load.execute).toHaveBeenCalledTimes(1);
    act(() => hook.result.current.next());
    await waitFor(() => expect(hook.result.current.directory).toBe(second));
    expect(load.execute.mock.calls[1]?.[2]).toBe("EMP-020");
    expect(hook.result.current.firstPage).toBe(false);
  });
  it.each(["employee", "company", "date"] as const)(
    "receipt discovery cancels the old request when %s changes",
    async (change) => {
      const employment = expenseIdentifier(100),
        pending = deferred<ExpenseDraftReceiptPage>(),
        receipts = toExpenseDraftReceiptPage(expenseDraftReceipts(), company, employment, null);
      const actions = {
        loadCategories: { execute: vi.fn() },
        loadCostCenters: { execute: vi.fn() },
        loadDraftReceipts: {
          execute: vi
            .fn<ExpenseUseCases["loadDraftReceipts"]["execute"]>()
            .mockReturnValueOnce(pending.promise)
            .mockResolvedValue(success(receipts)),
        },
      };
      const initial = { ...scope, employment, date: "2026-10-13" };
      const hook = renderHook(
        (input: typeof initial) =>
          useExpenseDraftOptions(
            input.account,
            input.access,
            actions,
            "receipt",
            input.employment,
            input.date,
          ),
        { initialProps: initial, wrapper: wrapper() },
      );
      await waitFor(() => expect(actions.loadDraftReceipts.execute).toHaveBeenCalledTimes(1));
      const old = actions.loadDraftReceipts.execute.mock.calls[0]?.[3],
        next = { ...initial };
      if (change === "employee") next.employment = expenseIdentifier(101);
      if (change === "company")
        next.access = { ...scope.access, companyId: expenseIdentifier(2) as CompanyId };
      if (change === "date") next.date = "2026-10-14";
      hook.rerender(next);
      expect(old?.aborted).toBe(true);
      await waitFor(() => expect(hook.result.current.page?.items).toHaveLength(2));
      await act(async () => pending.resolve(failed("employee_not_found")));
      expect(hook.result.current.failure).toBeNull();
      hook.unmount();
      expect(actions.loadCategories.execute).not.toHaveBeenCalled();
      expect(actions.loadCostCenters.execute).not.toHaveBeenCalled();
    },
  );
});
