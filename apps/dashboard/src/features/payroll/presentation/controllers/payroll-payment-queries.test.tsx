import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { payrollIdentifier } from "../../../../../tests/fixtures/payroll-payment-identifiers";
import {
  payrollPaymentList,
  payrollPaymentRecord,
  payrollPaymentReviewPage,
} from "../../../../../tests/fixtures/payroll-payments";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollPaymentPage } from "../../data/mappers/payroll-payment-mapper";
import { toPayrollPaymentReview } from "../../data/mappers/payroll-payment-review-mapper";
import type { PayrollPaymentBatchId } from "../../domain/entities/payroll-payment-batch";
import type { PayrollPaymentSearchInput } from "../../domain/entities/payroll-payment-search";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { usePayrollPayment } from "./use-payroll-payment";
import { usePayrollPayments } from "./use-payroll-payments";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
type Scope = {
  account: AccountId;
  access: CompanyAccess;
  search: PayrollPaymentSearchInput;
  id: string;
  after: string | null;
};
const scope: Scope = {
  account: payrollIdentifier(99) as AccountId,
  access: { companyId: company, permissions: ["payroll.pay"] },
  search: {
    from: "2026-10-01",
    until: "2026-10-31",
    status: null,
    after: null,
  },
  id: payrollIdentifier(80001),
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
  return toPayrollPaymentPage(payrollPaymentList([payrollPaymentRecord()]), company, {
    ...scope.search,
    status: null,
  });
}
const review = () =>
  toPayrollPaymentReview(
    payrollPaymentReviewPage(payrollPaymentRecord()),
    company,
    payrollIdentifier(80001) as PayrollPaymentBatchId,
    null,
  );
function directory(load: PayrollUseCases["loadPayments"]) {
  return renderHook(
    (input: Scope) => usePayrollPayments(input.account, input.access, input.search, load),
    { initialProps: scope, wrapper: providers() },
  );
}
function details(load: PayrollUseCases["loadPayment"]) {
  return renderHook(
    (input: Scope) => usePayrollPayment(input.account, input.access, input.id, input.after, load),
    { initialProps: scope, wrapper: providers() },
  );
}
describe("payroll payment query ownership", () => {
  it.each(["account", "company", "permissions", "dates", "status", "cursor"] as const)(
    "cancels the old directory when %s changes",
    async (change) => {
      const pending = deferred<ReturnType<typeof page>>(),
        current = page(),
        next = { ...scope };
      if (change === "account") next.account = payrollIdentifier(98) as AccountId;
      if (change === "company")
        next.access = { ...scope.access, companyId: payrollIdentifier(97) as CompanyId };
      if (change === "permissions")
        next.access = { ...scope.access, permissions: ["payroll.pay", "company.read"] };
      if (change === "dates") next.search = { ...scope.search, from: "2026-10-02" };
      if (change === "status") next.search = { ...scope.search, status: "RELEASED" };
      if (change === "cursor") next.search = { ...scope.search, after: payrollIdentifier(95) };
      const load = {
        execute: vi
          .fn<PayrollUseCases["loadPayments"]["execute"]>()
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
    },
  );
  it.each(["account", "company", "permissions", "batch", "history"] as const)(
    "cancels the old batch review when %s changes",
    async (change) => {
      const pending = deferred<ReturnType<typeof review>>(),
        current = review(),
        next = { ...scope };
      if (change === "account") next.account = payrollIdentifier(98) as AccountId;
      if (change === "company")
        next.access = { ...scope.access, companyId: payrollIdentifier(97) as CompanyId };
      if (change === "permissions")
        next.access = { ...scope.access, permissions: ["payroll.pay", "company.read"] };
      if (change === "batch") next.id = payrollIdentifier(96);
      if (change === "history") next.after = "4";
      const load = {
        execute: vi
          .fn<PayrollUseCases["loadPayment"]["execute"]>()
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
      execute: vi.fn<PayrollUseCases["loadPayments"]["execute"]>().mockReturnValue(pending.promise),
    };
    const hook = directory(load);
    await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
    const old = load.execute.mock.calls[0]?.[2];
    hook.unmount();
    expect(old?.aborted).toBe(true);
    await act(async () => pending.resolve(failed("access_denied")));
    await waitFor(() => expect(clients[0]?.getQueryCache().getAll()).toHaveLength(0));
  });
  it("releases disposed batch data without retaining a late successful review", async () => {
    const pending = deferred<ReturnType<typeof review>>();
    const load = {
      execute: vi.fn<PayrollUseCases["loadPayment"]["execute"]>().mockReturnValue(pending.promise),
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
        .fn<PayrollUseCases["loadPayments"]["execute"]>()
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
  it("hides batch data during refresh and preserves a safe failure until recovery", async () => {
    const current = review(),
      pending = deferred<ReturnType<typeof review>>();
    const load = {
      execute: vi
        .fn<PayrollUseCases["loadPayment"]["execute"]>()
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
  it("removes bank data after an export denial until an explicit new review", async () => {
    const current = review();
    const load = {
      execute: vi
        .fn<PayrollUseCases["loadPayment"]["execute"]>()
        .mockResolvedValue(success(current)),
    };
    const hook = details(load);
    await waitFor(() => expect(hook.result.current.review).toBe(current));
    act(() =>
      hook.result.current.withdrawReview(current, {
        code: "access_denied",
        fields: {},
        parameters: {},
      }),
    );
    await waitFor(() => expect(hook.result.current.review).toBeNull());
    expect(hook.result.current.failure?.code).toBe("access_denied");
    expect(load.execute).toHaveBeenCalledTimes(1);
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.review).toBe(current));
    expect(load.execute).toHaveBeenCalledTimes(2);
  });
  it("does not let an old worksheet denial overwrite a refreshed review", async () => {
    const old = review(),
      current = review();
    const load = {
      execute: vi
        .fn<PayrollUseCases["loadPayment"]["execute"]>()
        .mockResolvedValueOnce(success(old))
        .mockResolvedValue(success(current)),
    };
    const hook = details(load);
    await waitFor(() => expect(hook.result.current.review).toBe(old));
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.review).toBe(current));
    act(() =>
      hook.result.current.withdrawReview(old, {
        code: "access_denied",
        fields: {},
        parameters: {},
      }),
    );
    expect(hook.result.current.review).toBe(current);
    expect(hook.result.current.failure).toBeNull();
  });
});
