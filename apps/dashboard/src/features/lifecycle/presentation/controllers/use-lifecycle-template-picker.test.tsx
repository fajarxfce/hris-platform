import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { LifecycleTemplateId } from "../../domain/entities/lifecycle-template";
import type { LifecycleTemplatePage } from "../../domain/entities/lifecycle-template-page";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { useLifecycleTemplatePicker } from "./use-lifecycle-template-picker";

const account = "20000000-0000-4000-8000-000000000001" as AccountId;
const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const secondCompany = "10000000-0000-4000-8000-000000000002" as CompanyId;
const grants = ["people.lifecycle.read", "people.lifecycle.manage"];
const page: LifecycleTemplatePage = {
  items: Array.from({ length: 20 }, (_, n) => ({
    id: `90000000-abcd-4000-8000-${String(n + 1).padStart(12, "0")}` as LifecycleTemplateId,
    companyId: company,
    code: `TEMPLATE_${String(n + 1).padStart(3, "0")}`,
    name: `Checklist ${n + 1}`,
    kind: "ONBOARDING",
    active: n % 2 === 0,
    version: 2,
    tasks: [{ key: "equipment", title: "Equipment", required: true, dueDays: -2 }],
  })),
  nextCursor: "TEMPLATE_020",
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
    .fn<LifecycleUseCases["loadTemplates"]["execute"]>()
    .mockResolvedValue(success(page));
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const rendered = renderHook(
    ({ activeCompany, activeAccount, permissions }) =>
      useLifecycleTemplatePicker(
        activeAccount,
        { companyId: activeCompany, permissions },
        { execute },
      ),
    {
      wrapper,
      initialProps: { activeCompany: company, activeAccount: account, permissions: grants },
    },
  );
  return { ...rendered, client, execute };
}
describe("lifecycle template picker ownership", () => {
  it("keeps one explicit page, exposes active definitions and does not scan empty pages automatically", async () => {
    const f = fixture();
    await waitFor(() => expect(f.result.current.options).toHaveLength(10));
    expect(f.execute.mock.lastCall?.[1]).toBeNull();
    f.execute.mockResolvedValueOnce(
      success({
        items: page.items.map((template) => ({ ...template, active: false })),
        nextCursor: "TEMPLATE_040",
      }),
    );
    act(() => f.result.current.next());
    await waitFor(() => expect(f.result.current.nextCursor).toBe("TEMPLATE_040"));
    expect(f.execute.mock.lastCall?.[1]).toBe("TEMPLATE_020");
    expect(f.result.current.options).toEqual([]);
    expect(f.result.current.firstPage).toBe(false);
    expect(f.execute).toHaveBeenCalledTimes(2);
    await waitFor(() => expect(f.client.getQueryCache().getAll()).toHaveLength(1));
    act(() => f.result.current.first());
    await waitFor(() => expect(f.result.current.options).toHaveLength(10));
    expect(f.execute.mock.lastCall?.[1]).toBeNull();
  });
  it("cancels a previous account/company query and disposes its cache", async () => {
    const f = fixture();
    await waitFor(() => expect(f.result.current.options).toHaveLength(10));
    let resolve!: (value: Result<LifecycleTemplatePage>) => void;
    f.execute.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    act(() => f.result.current.refresh());
    await waitFor(() => expect(f.execute).toHaveBeenCalledTimes(2));
    const signal = f.execute.mock.lastCall?.[2];
    f.execute.mockResolvedValueOnce(
      success({
        items: page.items.map((template) => ({
          ...template,
          companyId: secondCompany,
          name: "Other company checklist",
        })),
        nextCursor: null,
      }),
    );
    f.rerender({
      activeCompany: secondCompany,
      activeAccount: "20000000-0000-4000-8000-000000000002" as AccountId,
      permissions: grants,
    });
    expect(signal?.aborted).toBe(true);
    await act(async () => {
      resolve(success(page));
    });
    await waitFor(() => expect(f.result.current.options[0]?.name).toBe("Other company checklist"));
    f.unmount();
    await waitFor(() => expect(f.client.getQueryCache().getAll()).toHaveLength(0));
  });
  it("removes selectable results on failure and requires an explicit retry", async () => {
    const f = fixture();
    await waitFor(() => expect(f.result.current.options).toHaveLength(10));
    f.execute.mockResolvedValueOnce(failed("mfa_required"));
    act(() => f.result.current.refresh());
    await waitFor(() => expect(f.result.current.failure?.code).toBe("mfa_required"));
    expect(f.result.current.options).toEqual([]);
    expect(f.execute).toHaveBeenCalledTimes(2);
    act(() => f.result.current.refresh());
    await waitFor(() => expect(f.result.current.options).toHaveLength(10));
    expect(f.execute).toHaveBeenCalledTimes(3);
    f.execute.mockResolvedValueOnce(failed("access_denied"));
    f.rerender({ activeCompany: company, activeAccount: account, permissions: [] });
    await waitFor(() => expect(f.result.current.failure?.code).toBe("access_denied"));
    expect(f.result.current.options).toEqual([]);
  });
});
