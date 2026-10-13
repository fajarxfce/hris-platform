import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  expenseClaimRecord,
  expenseClaimReviewPage,
  expenseIdentifier,
} from "../../../../../tests/fixtures/expense-claims";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toExpenseClaimPage } from "../../data/mappers/expense-claim-mapper";
import { toExpenseClaimReview } from "../../data/mappers/expense-claim-review-mapper";
import type { ExpenseClaimId } from "../../domain/entities/expense-claim";
import type { ExpenseClaimSearchInput } from "../../domain/entities/expense-claim-search";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { useExpenseClaim } from "./use-expense-claim";
import { useExpenseClaims } from "./use-expense-claims";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
type Scope = {
  account: AccountId;
  access: CompanyAccess;
  search: ExpenseClaimSearchInput;
  id: string;
  after: string | null;
};
const scope: Scope = {
  account: expenseIdentifier(99) as AccountId,
  access: { companyId: company, permissions: ["expenses.read"] },
  search: {
    from: "2026-10-01",
    until: "2026-10-31",
    status: null,
    employmentId: null,
    after: null,
  },
  id: expenseIdentifier(1),
  after: null,
};
const clients: QueryClient[] = [];
afterEach(() => {
  cleanup();
  for (const client of clients.splice(0)) client.clear();
});
function providers() {
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
  return { resolve, promise };
}
function page() {
  const record = expenseClaimRecord(),
    draft = record.claim.draft;
  return toExpenseClaimPage(
    {
      items: [
        {
          id: record.claim.id,
          employmentId: record.claim.employmentId,
          employeeNumber: draft.employeeNumber,
          employeeName: draft.employeeName,
          title: draft.title,
          totalAmount: draft.totalAmount,
          currency: "IDR",
          createdAt: record.claim.createdAt,
          status: "PENDING",
          version: 23,
        },
      ],
      nextCursor: null,
    },
    company,
    { ...scope.search, status: null },
  );
}
const review = () =>
  toExpenseClaimReview(
    expenseClaimReviewPage(expenseClaimRecord()),
    company,
    expenseIdentifier(1) as ExpenseClaimId,
    null,
  );
function directory(load: ExpenseUseCases["loadClaims"]) {
  return renderHook(
    (input: Scope) => useExpenseClaims(input.account, input.access, input.search, load),
    { initialProps: scope, wrapper: providers() },
  );
}
function details(load: ExpenseUseCases["loadClaim"]) {
  return renderHook(
    (input: Scope) => useExpenseClaim(input.account, input.access, input.id, input.after, load),
    { initialProps: scope, wrapper: providers() },
  );
}
describe("expense query ownership", () => {
  it.each([
    "account",
    "company",
    "permissions",
    "employment",
    "dates",
    "status",
    "cursor",
  ] as const)("cancels the old directory when %s changes", async (change) => {
    const pending = deferred<ReturnType<typeof page>>(),
      current = page(),
      next = { ...scope };
    if (change === "account") next.account = expenseIdentifier(98) as AccountId;
    if (change === "company")
      next.access = { ...scope.access, companyId: expenseIdentifier(97) as CompanyId };
    if (change === "permissions")
      next.access = { ...scope.access, permissions: ["expenses.manage"] };
    if (change === "employment")
      next.search = { ...scope.search, employmentId: expenseIdentifier(96) };
    if (change === "dates") next.search = { ...scope.search, from: "2026-10-02" };
    if (change === "status") next.search = { ...scope.search, status: "PENDING" };
    if (change === "cursor") next.search = { ...scope.search, after: expenseIdentifier(95) };
    const load = {
      execute: vi
        .fn<ExpenseUseCases["loadClaims"]["execute"]>()
        .mockReturnValueOnce(pending.promise)
        .mockResolvedValue(success(current)),
    };
    const hook = directory(load);
    await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
    const old = load.execute.mock.calls[0]?.[2];
    hook.rerender(next);
    expect(old?.aborted).toBe(true);
    await waitFor(() => expect(hook.result.current.page).toBe(current));
    await act(async () => pending.resolve(failed("mfa_required")));
    expect(hook.result.current.page).toBe(current);
    expect(hook.result.current.failure).toBeNull();
    expect(load.execute).toHaveBeenCalledTimes(2);
  });
  it.each(["account", "company", "permissions", "claim", "history"] as const)(
    "cancels the old claim review when %s changes",
    async (change) => {
      const pending = deferred<ReturnType<typeof review>>(),
        current = review(),
        next = { ...scope };
      if (change === "account") next.account = expenseIdentifier(98) as AccountId;
      if (change === "company")
        next.access = { ...scope.access, companyId: expenseIdentifier(97) as CompanyId };
      if (change === "permissions")
        next.access = { ...scope.access, permissions: ["expenses.manage"] };
      if (change === "claim") next.id = expenseIdentifier(96);
      if (change === "history") next.after = "4";
      const load = {
        execute: vi
          .fn<ExpenseUseCases["loadClaim"]["execute"]>()
          .mockReturnValueOnce(pending.promise)
          .mockResolvedValue(success(current)),
      };
      const hook = details(load);
      await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
      const old = load.execute.mock.calls[0]?.[3];
      hook.rerender(next);
      expect(old?.aborted).toBe(true);
      await waitFor(() => expect(hook.result.current.review).toBe(current));
      await act(async () => pending.resolve(success(review())));
      expect(hook.result.current.review).toBe(current);
      expect(load.execute).toHaveBeenCalledTimes(2);
    },
  );
  it("releases a disposed directory and ignores a late access denial", async () => {
    const pending = deferred<ReturnType<typeof page>>();
    const load = {
      execute: vi.fn<ExpenseUseCases["loadClaims"]["execute"]>().mockReturnValue(pending.promise),
    };
    const hook = directory(load);
    await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
    const old = load.execute.mock.calls[0]?.[2];
    hook.unmount();
    expect(old?.aborted).toBe(true);
    await act(async () => pending.resolve(failed("access_denied")));
    await waitFor(() => expect(clients[0]?.getQueryCache().getAll()).toHaveLength(0));
  });
  it("releases disposed claim data without retaining a late successful review", async () => {
    const pending = deferred<ReturnType<typeof review>>();
    const load = {
      execute: vi.fn<ExpenseUseCases["loadClaim"]["execute"]>().mockReturnValue(pending.promise),
    };
    const hook = details(load);
    await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
    const old = load.execute.mock.calls[0]?.[3];
    hook.unmount();
    expect(old?.aborted).toBe(true);
    await act(async () => pending.resolve(success(review())));
    await waitFor(() => expect(clients[0]?.getQueryCache().getAll()).toHaveLength(0));
  });
  it("does not refetch for equivalent scope and hides the directory until an explicit recovery succeeds", async () => {
    const current = page(),
      pending = deferred<ReturnType<typeof page>>();
    const load = {
      execute: vi
        .fn<ExpenseUseCases["loadClaims"]["execute"]>()
        .mockResolvedValueOnce(success(current))
        .mockReturnValueOnce(pending.promise)
        .mockResolvedValue(success(current)),
    };
    const hook = directory(load);
    await waitFor(() => expect(hook.result.current.page).toBe(current));
    hook.rerender({
      ...scope,
      access: { ...scope.access, permissions: [...scope.access.permissions] },
    });
    expect(load.execute).toHaveBeenCalledTimes(1);
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.loading).toBe(true));
    expect(hook.result.current.page).toBeNull();
    await act(async () => pending.resolve(failed("mfa_required")));
    await waitFor(() => expect(hook.result.current.failure?.code).toBe("mfa_required"));
    expect(hook.result.current.page).toBeNull();
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.page).toBe(current));
    expect(load.execute).toHaveBeenCalledTimes(3);
  });
  it("hides claim data during refresh and preserves a safe failure until recovery", async () => {
    const current = review(),
      pending = deferred<ReturnType<typeof review>>();
    const load = {
      execute: vi
        .fn<ExpenseUseCases["loadClaim"]["execute"]>()
        .mockResolvedValueOnce(success(current))
        .mockReturnValueOnce(pending.promise)
        .mockResolvedValue(success(current)),
    };
    const hook = details(load);
    await waitFor(() => expect(hook.result.current.review).toBe(current));
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.loading).toBe(true));
    expect(hook.result.current.review).toBeNull();
    await act(async () => pending.resolve(failed("company_access_denied")));
    await waitFor(() => expect(hook.result.current.failure?.code).toBe("company_access_denied"));
    expect(hook.result.current.review).toBeNull();
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.review).toBe(current));
  });
});
