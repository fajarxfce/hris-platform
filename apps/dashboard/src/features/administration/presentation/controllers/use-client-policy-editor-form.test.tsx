import { act, cleanup, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import { WorkspaceNavigationContext } from "../../../../core/presentation/navigation/use-navigation-protection";
import { WorkspaceNavigationController } from "../../../../core/presentation/navigation/workspace-navigation-controller";
import type { ClientPolicySettings } from "../../domain/entities/client-policy-settings";
import {
  type ClientPolicyEditorState,
  initialClientPolicyEditorState,
} from "../models/client-policy-editor-state";
import {
  clientPolicyChangeFromFields,
  clientPolicyFormValues,
} from "../models/client-policy-form-values";
import { ClientPolicyEditorController } from "./client-policy-editor-controller";
import { useClientPolicyEditorForm } from "./use-client-policy-editor-form";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const settings = (): ClientPolicySettings => ({
  companyId,
  latest: {
    companyId,
    version: 1,
    activateAt: "2026-11-01T00:00:00.123456Z",
    disabledModules: ["PAYROLL"],
    minimumBuilds: { android: 42, ios: 19, web: 0 },
    maintenance: { startsAt: "2026-11-01T00:00:00.123456Z", endsAt: "2026-11-01T00:30:00.123456Z" },
    recordedAt: "2026-10-01T00:00:00Z",
    actorId: "20000000-0000-4000-8000-000000000001" as AccountId,
    reason: "Existing head",
  },
  effective: {
    version: 0,
    enabledModules: ["PEOPLE"],
    minimumBuilds: { android: 1, ios: 0, web: 0 },
    maintenance: null,
    maintenanceActive: false,
    evaluatedAt: "2026-10-10T00:00:00Z",
    validUntil: "2026-10-10T00:01:00Z",
  },
});
afterEach(cleanup);

describe("client policy form ownership", () => {
  it("loads the configured head and preserves exact UTC instants without reusing its audit reason", () => {
    const values = clientPolicyFormValues(settings());
    expect(values).toMatchObject({
      activation: "SCHEDULED",
      activateAt: "2026-11-01T00:00:00.123456Z",
      disabledModules: ["PAYROLL"],
      android: "42",
      ios: "19",
      web: "0",
      maintenanceEnabled: true,
      maintenanceStarts: "2026-11-01T00:00:00.123456Z",
      reason: "",
    });
    expect(clientPolicyChangeFromFields(values)).toMatchObject({
      activateAt: "2026-11-01T00:00:00.123456Z",
      minimumBuilds: { android: 42, ios: 19, web: 0 },
      maintenance: {
        startsAt: "2026-11-01T00:00:00.123456Z",
        endsAt: "2026-11-01T00:30:00.123456Z",
      },
    });
    expect(clientPolicyFormValues({ ...settings(), latest: null })).toEqual(
      clientPolicyFormValues(null),
    );
  });

  it("uses immediate activation for an effective head and does not turn empty builds into zero", () => {
    const snapshot = settings();
    if (!snapshot.latest) throw new Error("Expected a head");
    const values = clientPolicyFormValues({
      ...snapshot,
      latest: { ...snapshot.latest, activateAt: "2026-10-10T00:00:00.000000Z" },
    });
    expect(values.activation).toBe("IMMEDIATE");
    const change = clientPolicyChangeFromFields({
      ...values,
      maintenanceEnabled: false,
      android: "",
      ios: " ",
      web: "12",
    });
    expect(change).toMatchObject({
      activateAt: null,
      maintenance: null,
      minimumBuilds: { android: Number.NaN, ios: Number.NaN, web: 12 },
    });
  });

  it("retains edited values during verification and resets even an identical snapshot only after explicit reload", async () => {
    const navigation = new WorkspaceNavigationController();
    const controller = new ClientPolicyEditorController(
      {
        loadClientPolicy: { execute: vi.fn().mockResolvedValue(failed("access_denied")) },
        saveClientPolicy: { execute: vi.fn().mockResolvedValue(failed("access_denied")) },
      },
      { companyId, permissions: ["settings.manage"] },
      () => "30000000-0000-4000-8000-000000000001",
    );
    const refresh = vi.spyOn(controller, "refresh").mockResolvedValue();
    const initial: ClientPolicyEditorState = {
      ...initialClientPolicyEditorState,
      stage: "editing",
      settings: settings(),
    };
    const hook = renderHook(
      ({ state, build }) => useClientPolicyEditorForm(controller, state, build),
      {
        initialProps: { state: initial, build: 1 },
        wrapper: ({ children }: { children: ReactNode }) => (
          <WorkspaceNavigationContext value={navigation}>{children}</WorkspaceNavigationContext>
        ),
      },
    );
    await act(async () => {
      hook.result.current.reason.onChange("Retain draft");
      hook.result.current.web.onChange("3");
      hook.result.current.setModule("PEOPLE", false);
    });
    expect(navigation.getSnapshot().protection).toBe("dirty");
    await act(async () =>
      hook.rerender({
        state: {
          ...initial,
          failure: { code: "recent_authentication_required", fields: {}, parameters: {} },
        },
        build: 2,
      }),
    );
    expect(hook.result.current.reason.value).toBe("Retain draft");
    expect(hook.result.current.web.value).toBe("3");
    expect(hook.result.current.webRequiresUpdate).toBe(true);
    expect(hook.result.current.modules.find((item) => item.key === "PEOPLE")?.enabled).toBe(false);
    await act(async () => hook.result.current.refresh());
    expect(navigation.getSnapshot().deciding).toBe(true);
    expect(refresh).not.toHaveBeenCalled();
    await act(async () => navigation.leave());
    expect(refresh).toHaveBeenCalledOnce();
    await act(async () => hook.rerender({ state: { ...initial, settings: settings() }, build: 2 }));
    expect(hook.result.current.reason.value).toBe("");
    expect(hook.result.current.web.value).toBe("0");
    expect(navigation.getSnapshot().protection).toBe("none");
    hook.unmount();
    expect(navigation.getSnapshot()).toEqual({ protection: "none", deciding: false });
  });

  it("keeps uncertain fields immutable and releases navigation ownership on disposal", async () => {
    const navigation = new WorkspaceNavigationController();
    const controller = new ClientPolicyEditorController(
      {
        loadClientPolicy: { execute: vi.fn().mockResolvedValue(failed("access_denied")) },
        saveClientPolicy: { execute: vi.fn().mockResolvedValue(failed("access_denied")) },
      },
      { companyId, permissions: ["settings.manage"] },
      () => "30000000-0000-4000-8000-000000000001",
    );
    const state: ClientPolicyEditorState = {
      ...initialClientPolicyEditorState,
      stage: "unconfirmed",
      settings: settings(),
    };
    const hook = renderHook(() => useClientPolicyEditorForm(controller, state, 1), {
      wrapper: ({ children }: { children: ReactNode }) => (
        <WorkspaceNavigationContext value={navigation}>{children}</WorkspaceNavigationContext>
      ),
    });
    expect(hook.result.current.editable).toBe(false);
    expect(navigation.getSnapshot().protection).toBe("unconfirmed");
    await act(async () => hook.result.current.setModule("PEOPLE", false));
    expect(hook.result.current.modules.find((item) => item.key === "PEOPLE")?.enabled).toBe(true);
    hook.unmount();
    expect(navigation.getSnapshot().protection).toBe("none");
  });
});
