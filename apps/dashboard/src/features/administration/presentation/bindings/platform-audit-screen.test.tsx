import { act, render, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { expect, it, vi } from "vitest";
import type { AccountId } from "../../../../core/domain/identifiers";
import { success } from "../../../../core/domain/result";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { PlatformAuditScreen } from "./platform-audit-screen";

// Lifecycle is tested here; actual Fluent rendering/focus is covered in Chromium.
vi.mock("../pages/audit-page", () => ({
  AuditPage: ({ state }: { state: { stage: string } }) => (
    <p>{state.stage === "ready" ? "No events match these filters." : "Unloaded"}</p>
  ),
}));

it("hiding or changing the account aborts reads and discards late page results", async () => {
  const empty = {
    from: "2026-10-01T00:00:00Z",
    until: "2026-10-31T00:00:00Z",
    evaluatedAt: "2026-10-31T00:00:00Z",
    items: [],
    nextCursor: null,
  };
  let resolve: ((value: ReturnType<typeof success<typeof empty>>) => void) | undefined;
  const first = new Promise<ReturnType<typeof success<typeof empty>>>((accept) => {
    resolve = accept;
  });
  const execute = vi
    .fn<AdministrationUseCases["searchPlatformAudit"]["execute"]>()
    .mockReturnValueOnce(first)
    .mockResolvedValue(success(empty));
  const view = (accountId: string, visible: boolean) => (
    <MemoryRouter>
      <WorkspaceVisibilityContext.Provider value={visible}>
        <PlatformAuditScreen
          access={{ accountId: accountId as AccountId, permissions: ["audit.read"] }}
          search={{ execute }}
          locale="en"
        />
      </WorkspaceVisibilityContext.Provider>
    </MemoryRouter>
  );
  const screen = render(view("account-a", true));
  await waitFor(() => expect(execute).toHaveBeenCalledTimes(1));
  const original = execute.mock.calls[0]?.[2];
  screen.rerender(view("account-a", false));
  expect(original?.aborted).toBe(true);
  await act(async () => {
    resolve?.(success(empty));
    await first;
  });
  expect(screen.queryByText("No events match these filters.")).toBeNull();
  screen.rerender(view("account-b", true));
  await waitFor(() => expect(execute).toHaveBeenCalledTimes(2));
  expect(execute.mock.calls[1]?.[0].accountId).toBe("account-b");
  await waitFor(() => expect(screen.getByText("No events match these filters.")).toBeDefined());
  screen.unmount();
});
