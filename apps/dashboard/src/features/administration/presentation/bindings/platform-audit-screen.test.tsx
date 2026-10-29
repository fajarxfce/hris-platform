import { act, fireEvent, render, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { expect, it, vi } from "vitest";
import type { AccountId } from "../../../../core/domain/identifiers";
import { success } from "../../../../core/domain/result";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { PlatformAuditScreen } from "./platform-audit-screen";

// Lifecycle is tested here; actual Fluent rendering/focus is covered in Chromium.
vi.mock("../pages/audit-page", () => ({
  AuditPage: ({
    state,
    details,
    onOpen,
    onClose,
  }: {
    state: { stage: string };
    details: React.ReactNode;
    onOpen: (id: string) => void;
    onClose: () => void;
  }) => (
    <div>
      <p>{state.stage === "ready" ? "No events match these filters." : "Unloaded"}</p>
      <button type="button" onClick={() => onOpen("40000000-0000-4000-8000-000000000001")}>
        Open event
      </button>
      <button type="button" onClick={onClose}>
        Close event
      </button>
      {details}
    </div>
  ),
}));

vi.mock("../pages/platform-account-audit-panel", () => ({
  PlatformAccountAuditPanel: ({ state }: { state: { review: { reason: string } | null } }) => (
    <p>{state.review?.reason ?? "Detail unloaded"}</p>
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
          detail={{ execute: vi.fn() }}
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

it("closing, hiding, changing account or losing detail permission aborts the private read", async () => {
  const accountId = "account-a" as AccountId;
  const event = {
    id: "40000000-0000-4000-8000-000000000001",
    actorId: accountId,
    resourceId: "30000000-0000-4000-8000-000000000001",
    resourceType: "account",
    action: "identity.account_access_saved",
    correlationId: "50000000-0000-4000-8000-000000000001",
    recordedAt: "2026-10-01T00:00:00Z",
  };
  const result = success({
    id: event.id,
    accountId: event.resourceId,
    active: true,
    permissions: ["audit.read"],
    reason: "PRIVATE SAVED REASON",
  });
  const search = {
    execute: vi.fn<AdministrationUseCases["searchPlatformAudit"]["execute"]>().mockResolvedValue(
      success({
        from: "2026-10-01T00:00:00Z",
        until: "2026-10-31T00:00:00Z",
        evaluatedAt: "2026-10-31T00:00:00Z",
        items: [event],
        nextCursor: null,
      }),
    ),
  };
  for (const transition of ["close", "hidden", "account", "permission"]) {
    let resolve!: (value: typeof result) => void;
    const pending = new Promise<typeof result>((accept) => {
      resolve = accept;
    });
    const execute = vi
      .fn<AdministrationUseCases["getPlatformAccountAudit"]["execute"]>()
      .mockReturnValueOnce(pending)
      .mockResolvedValue(result);
    const detail = { execute };
    const view = (changed = false) => (
      <MemoryRouter>
        <WorkspaceVisibilityContext.Provider value={!(changed && transition === "hidden")}>
          <PlatformAuditScreen
            access={{
              accountId: (changed && transition === "account"
                ? "account-b"
                : accountId) as AccountId,
              permissions:
                changed && transition === "permission"
                  ? ["audit.read"]
                  : ["audit.read", "identity.manage"],
            }}
            search={search}
            detail={detail}
            locale="en"
          />
        </WorkspaceVisibilityContext.Provider>
      </MemoryRouter>
    );
    const screen = render(view());
    await waitFor(() => expect(screen.getByText("No events match these filters.")).toBeDefined());
    fireEvent.click(screen.getByText("Open event"));
    await waitFor(() => expect(execute).toHaveBeenCalledTimes(1));
    if (transition === "close") fireEvent.click(screen.getByText("Close event"));
    else screen.rerender(view(true));
    expect(execute.mock.calls[0]?.[2].aborted).toBe(true);
    await act(async () => {
      resolve(result);
      await pending;
    });
    expect(screen.queryByText("PRIVATE SAVED REASON")).toBeNull();
    if (transition === "permission") {
      await waitFor(() => expect(screen.getByText("No events match these filters.")).toBeDefined());
      fireEvent.click(screen.getByText("Open event"));
      expect(execute).toHaveBeenCalledTimes(1);
    }
    screen.unmount();
  }
});
