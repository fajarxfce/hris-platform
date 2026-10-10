import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { LifecycleAssigneePage } from "../../domain/entities/lifecycle-assignee";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { useLifecycleAssigneePicker } from "./use-lifecycle-assignee-picker";

const account = "20000000-0000-4000-8000-000000000001" as AccountId;
const secondAccount = "20000000-0000-4000-8000-000000000002" as AccountId;
const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const secondCompany = "10000000-0000-4000-8000-000000000002" as CompanyId;
const page: LifecycleAssigneePage = {
  items: [
    { id: account, companyId: company, displayName: "Current member" },
    { id: secondAccount, companyId: company, displayName: "Next member" },
  ],
  nextCursor: null,
};
const clients: QueryClient[] = [];
afterEach(() => {
  cleanup();
  for (const client of clients.splice(0)) client.clear();
});
function fixture() {
  const client = new QueryClient();
  clients.push(client);
  const execute = vi
    .fn<LifecycleUseCases["loadAssignees"]["execute"]>()
    .mockResolvedValue(success(page));
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const rendered = renderHook(
    ({ activeAccount, activeCompany }) =>
      useLifecycleAssigneePicker(
        activeAccount,
        { companyId: activeCompany, permissions: ["people.lifecycle.manage"] },
        { execute },
        account,
      ),
    { wrapper, initialProps: { activeAccount: account, activeCompany: company } },
  );
  return { ...rendered, client, execute };
}
describe("lifecycle assignee query ownership", () => {
  it("uses explicit search and cursor pages, excludes the current member and retains one page", async () => {
    const f = fixture();
    await waitFor(() => expect(f.result.current.options).toHaveLength(1));
    expect(f.result.current.options[0]?.displayName).toBe("Next member");
    await act(async () => {
      await f.result.current.query.onChange({ target: { value: " Lookup%_ " } });
    });
    expect(f.execute).toHaveBeenCalledOnce();
    f.execute.mockResolvedValueOnce(success({ ...page, nextCursor: secondAccount }));
    await act(async () => {
      await f.result.current.apply();
    });
    await waitFor(() => expect(f.result.current.nextCursor).toBe(secondAccount));
    expect(f.execute.mock.lastCall?.[1]).toBe("Lookup%_");
    f.execute.mockResolvedValueOnce(success({ items: [], nextCursor: null }));
    act(() => f.result.current.next());
    await waitFor(() => expect(f.result.current.loading).toBe(false));
    expect(f.execute.mock.lastCall?.[2]).toBe(secondAccount);
    expect(f.result.current.firstPage).toBe(false);
    await waitFor(() => expect(f.client.getQueryCache().getAll()).toHaveLength(1));
  });
  it("cancels a superseded account/company query and releases its cache after disposal", async () => {
    const f = fixture();
    await waitFor(() => expect(f.result.current.options).toHaveLength(1));
    let resolve!: (value: Result<LifecycleAssigneePage>) => void;
    f.execute.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    act(() => f.result.current.refresh());
    await waitFor(() => expect(f.execute).toHaveBeenCalledTimes(2));
    const signal = f.execute.mock.lastCall?.[3];
    f.execute.mockResolvedValueOnce(
      success({
        items: [
          { id: secondAccount, companyId: secondCompany, displayName: "Other company member" },
        ],
        nextCursor: null,
      }),
    );
    f.rerender({ activeAccount: secondAccount, activeCompany: secondCompany });
    expect(signal?.aborted).toBe(true);
    await act(async () => {
      resolve(success(page));
    });
    await waitFor(() =>
      expect(f.result.current.options[0]?.displayName).toBe("Other company member"),
    );
    f.unmount();
    await waitFor(() => expect(f.client.getQueryCache().getAll()).toHaveLength(0));
  });
  it("clears failed results and repeats the same search only when requested", async () => {
    const f = fixture();
    await waitFor(() => expect(f.result.current.options).toHaveLength(1));
    f.execute.mockResolvedValueOnce(failed("mfa_required"));
    await act(async () => {
      await f.result.current.apply();
    });
    await waitFor(() => expect(f.result.current.failure?.code).toBe("mfa_required"));
    expect(f.result.current.options).toEqual([]);
    expect(f.execute).toHaveBeenCalledTimes(2);
    await act(async () => {
      await f.result.current.apply();
    });
    await waitFor(() => expect(f.result.current.options).toHaveLength(1));
    expect(f.execute).toHaveBeenCalledTimes(3);
  });
});
