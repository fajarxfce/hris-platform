import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  documentCompanyId,
  documentEmployeeDirectory,
  documentId,
} from "../../../../../tests/fixtures/documents";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toDocumentEmployees } from "../../data/mappers/document-employee-mapper";
import type { DocumentEmployeeDirectory } from "../../domain/entities/document-employee";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { useDocumentEmployees } from "./use-document-reads";

const search = { query: "", after: null };
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
function pending() {
  let resolve!: (value: Result<DocumentEmployeeDirectory>) => void;
  const promise = new Promise<Result<DocumentEmployeeDirectory>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function setup() {
  const data = toDocumentEmployees(
    documentEmployeeDirectory(),
    documentCompanyId as CompanyId,
    search,
    null,
  );
  const load = {
    execute: vi
      .fn<DocumentsScreenProps["documents"]["loadEmployees"]["execute"]>()
      .mockResolvedValue(success(data)),
  };
  const unused = { execute: vi.fn().mockResolvedValue(failed("request_failed")) };
  const scope: DocumentsScreenProps = {
    accountId: documentId(90) as AccountId,
    access: {
      companyId: documentCompanyId as CompanyId,
      permissions: ["documents.read", "people.profile.read"],
    },
    companyName: "Company",
    timezone: "Asia/Jakarta",
    locale: "en",
    nextIdentifier: () => documentId(80),
    documents: {
      retention: {
        reviewRetirement: unused,
        retireRevision: unused,
        load: unused,
        reviewChange: unused,
        change: unused,
        loadPolicies: unused,
        loadPolicy: unused,
        reviewPolicy: unused,
        savePolicy: unused,
      },
      reviewUpload: unused,
      selectUploadFile: unused,
      releaseUploadFile: { execute: vi.fn() },
      uploadFile: unused,
      loadEmployees: load,
      loadFolder: unused,
      loadReview: unused,
      loadRevision: unused,
      download: unused,
      reviewAction: unused,
      validate: unused,
      cancelUpload: unused,
    },
  };
  return { data, load, scope };
}
describe("document query ownership", () => {
  it.each(["company", "account", "permissions"] as const)(
    "cancels and discards a pending read when %s changes",
    async (change) => {
      const { data, load, scope } = setup(),
        held = pending();
      load.execute
        .mockReturnValueOnce(held.promise)
        .mockResolvedValue(success({ ...data, query: "new scope" }));
      const hook = renderHook((props) => useDocumentEmployees(props, search), {
        wrapper: wrapper(),
        initialProps: scope,
      });
      await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
      hook.rerender({
        ...scope,
        accountId: change === "account" ? (documentId(91) as AccountId) : scope.accountId,
        access: {
          companyId: change === "company" ? (documentId(2) as CompanyId) : scope.access.companyId,
          permissions: change === "permissions" ? ["documents.read"] : scope.access.permissions,
        },
      });
      expect(load.execute.mock.calls[0]?.[2].aborted).toBe(true);
      await waitFor(() => expect(hook.result.current.data?.query).toBe("new scope"));
      await act(async () => held.resolve(success(data)));
      expect(hook.result.current.data?.query).toBe("new scope");
      expect(load.execute).toHaveBeenCalledTimes(2);
    },
  );

  it("hides prior data during refresh, retains failure and requires explicit recovery", async () => {
    const { data, load, scope } = setup(),
      held = pending();
    const hook = renderHook(() => useDocumentEmployees(scope, search), { wrapper: wrapper() });
    await waitFor(() => expect(hook.result.current.data).toBe(data));
    load.execute.mockReturnValueOnce(held.promise);
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.loading).toBe(true));
    expect(hook.result.current.data).toBeNull();
    await act(async () => held.resolve(failed("document_access_denied")));
    await waitFor(() => expect(hook.result.current.failure?.code).toBe("document_access_denied"));
    hook.rerender();
    expect(hook.result.current.data).toBeNull();
    expect(load.execute).toHaveBeenCalledTimes(2);
    const latest = { ...data };
    load.execute.mockResolvedValueOnce(success(latest));
    act(() => hook.result.current.refresh());
    await waitFor(() => expect(hook.result.current.data).toBe(latest));
    expect(hook.result.current.failure).toBeNull();
  });

  it("cancels pending reads on disposal, clears the cache and does not retry late failures", async () => {
    const { load, scope } = setup(),
      held = pending();
    load.execute.mockReturnValueOnce(held.promise);
    const hook = renderHook(() => useDocumentEmployees(scope, search), { wrapper: wrapper() });
    await waitFor(() => expect(load.execute).toHaveBeenCalledTimes(1));
    hook.unmount();
    expect(load.execute.mock.calls[0]?.[2].aborted).toBe(true);
    await act(async () => held.resolve(failed("mfa_required")));
    expect(load.execute).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(clients[0]?.getQueryCache().getAll()).toHaveLength(0));
  });
});
