import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, it, vi } from "vitest";
import { roleTemplateReview } from "../../../../../tests/fixtures/role-templates";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { RoleTemplateId, RoleTemplatePage } from "../../domain/entities/role-template";
import type { LoadRoleTemplates } from "../../domain/usecases/load-role-templates";
import { useCompanyRolePicker } from "./use-company-role-picker";

const account = "20000000-0000-4000-8000-000000000001" as AccountId;
const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const clients: QueryClient[] = [];
afterEach(() => {
  cleanup();
  for (const client of clients.splice(0)) client.clear();
});
function pending<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function page(companyId: CompanyId, index = 1): RoleTemplatePage {
  const role = roleTemplateReview(index).current;
  return { items: [{ ...role, id: role.id as RoleTemplateId, companyId }], nextCursor: null };
}
function fixture() {
  const client = new QueryClient();
  clients.push(client);
  const load = {
    execute: vi
      .fn<LoadRoleTemplates["execute"]>()
      .mockImplementation(async (access) => success(page(access.companyId))),
  };
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const hook = renderHook(
    ({ account, company }: { account: AccountId; company: CompanyId }) =>
      useCompanyRolePicker(account, { companyId: company, permissions: ["identity.manage"] }, load),
    { wrapper, initialProps: { account, company } },
  );
  return { ...hook, load, client };
}

it("owns a single role page and advances only on an explicit pagination action", async () => {
  const f = fixture();
  await waitFor(() => expect(f.result.current.items).toHaveLength(1));
  const first = page(company);
  const cursor = first.items[0]?.id;
  if (!cursor) throw new Error("Missing role fixture");
  f.load.execute.mockResolvedValueOnce(success({ ...first, nextCursor: cursor }));
  act(() => f.result.current.refresh());
  await waitFor(() => expect(f.result.current.nextCursor).toBe(cursor));
  f.load.execute.mockResolvedValueOnce(success(page(company, 2)));
  act(() => f.result.current.next());
  await waitFor(() => expect(f.result.current.items[0]?.name).toBe("Operations reviewer 2"));
  expect(f.load.execute.mock.lastCall?.[1]).toBe(cursor);
  await waitFor(() => expect(f.client.getQueryCache().getAll()).toHaveLength(1));
  act(() => f.result.current.first());
  await waitFor(() => expect(f.result.current.firstPage).toBe(true));
  await waitFor(() => expect(f.result.current.loading).toBe(false));
  expect(f.load.execute.mock.lastCall?.[1]).toBeNull();
});

it.each(["account", "company"])(
  "cancels and discards an obsolete role lookup on %s replacement",
  async (scope) => {
    const f = fixture();
    await waitFor(() => expect(f.result.current.items).toHaveLength(1));
    const held = pending<Result<RoleTemplatePage>>();
    f.load.execute.mockReturnValueOnce(held.promise);
    act(() => f.result.current.refresh());
    await waitFor(() => expect(f.result.current.loading).toBe(true));
    const signal = f.load.execute.mock.lastCall?.[2];
    const nextCompany =
      scope === "company" ? ("10000000-0000-4000-8000-000000000002" as CompanyId) : company;
    const nextAccount =
      scope === "account" ? ("20000000-0000-4000-8000-000000000002" as AccountId) : account;
    f.rerender({ account: nextAccount, company: nextCompany });
    await waitFor(() => expect(f.result.current.items[0]?.companyId).toBe(nextCompany));
    expect(signal?.aborted).toBe(true);
    held.resolve(success(page(company, 9)));
    await act(async () => {
      await held.promise;
    });
    expect(f.result.current.items[0]?.name).toBe("Operations reviewer 1");
    await waitFor(() => expect(f.client.getQueryCache().getAll()).toHaveLength(1));
  },
);

it("clears old options when refreshing fails and does not retry silently", async () => {
  const f = fixture();
  await waitFor(() => expect(f.result.current.items).toHaveLength(1));
  f.load.execute.mockResolvedValueOnce(failed("access_denied"));
  act(() => f.result.current.refresh());
  await waitFor(() => expect(f.result.current.failure?.code).toBe("access_denied"));
  expect(f.result.current.items).toEqual([]);
  expect(f.load.execute).toHaveBeenCalledTimes(2);
});

it("removes the lookup and aborts pending work when the picker closes", async () => {
  const f = fixture();
  await waitFor(() => expect(f.result.current.items).toHaveLength(1));
  const held = pending<Result<RoleTemplatePage>>();
  f.load.execute.mockReturnValueOnce(held.promise);
  act(() => f.result.current.refresh());
  await waitFor(() => expect(f.result.current.loading).toBe(true));
  const signal = f.load.execute.mock.lastCall?.[2];
  f.unmount();
  expect(signal?.aborted).toBe(true);
  held.resolve(success(page(company)));
  await waitFor(() => expect(f.client.getQueryCache().getAll()).toHaveLength(0));
});
