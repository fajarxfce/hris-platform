import { act, cleanup, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { WorkspaceNavigationContext } from "../../../../core/presentation/navigation/use-navigation-protection";
import { WorkspaceNavigationController } from "../../../../core/presentation/navigation/workspace-navigation-controller";
import {
  initialPayrollCommandState,
  type PayrollCommandState,
} from "../models/payroll-command-state";
import { usePayrollReasonForm } from "./use-payroll-reason-form";

function fixture() {
  const navigation = new WorkspaceNavigationController(),
    controller = { save: vi.fn(async () => {}), refresh: vi.fn(async () => {}) },
    ready: PayrollCommandState<unknown> = {
      ...initialPayrollCommandState(),
      stage: "reviewing",
      review: {},
    };
  const hook = renderHook(
    ({ state }: { state: PayrollCommandState<unknown> }) => usePayrollReasonForm(controller, state),
    {
      initialProps: { state: ready },
      wrapper: ({ children }: { children: ReactNode }) => (
        <WorkspaceNavigationContext value={navigation}>{children}</WorkspaceNavigationContext>
      ),
    },
  );
  return { ...hook, navigation, controller, ready };
}
afterEach(cleanup);
describe("payroll reason form ownership", () => {
  it("retains the reason across equivalent reviews and protects explicit reload", async () => {
    const f = fixture();
    await act(async () => f.result.current.reason.onChange("Reviewed correction"));
    f.rerender({ state: { ...f.ready, review: {} } });
    expect(f.result.current.reason.value).toBe("Reviewed correction");
    act(() => f.result.current.reload());
    expect(f.navigation.getSnapshot()).toMatchObject({ deciding: true, protection: "dirty" });
    expect(f.controller.refresh).not.toHaveBeenCalled();
    act(() => f.navigation.stay());
    expect(f.result.current.reason.value).toBe("Reviewed correction");
    act(() => f.result.current.reload());
    act(() => f.navigation.leave());
    expect(f.controller.refresh).toHaveBeenCalledTimes(1);
    expect(f.result.current.reason.value).toBe("");
    f.unmount();
  });
  it("clears private text on denied acquisition while preserving uncertain navigation protection", async () => {
    const f = fixture();
    await act(async () => f.result.current.reason.onChange("Private evidence reference"));
    f.rerender({ state: { ...f.ready, stage: "unconfirmed", review: null } });
    expect(f.result.current.reason.value).toBe("");
    expect(f.navigation.getSnapshot().protection).toBe("unconfirmed");
    f.unmount();
    expect(f.navigation.getSnapshot().protection).toBe("none");
  });
  it("blocks empty reasons and releases pending form protection after completion", async () => {
    const f = fixture();
    await act(async () => f.result.current.submit());
    expect(f.controller.save).not.toHaveBeenCalled();
    expect(f.result.current.invalidReason).toBe(true);
    await act(async () => f.result.current.reason.onChange("Confirmed"));
    await act(async () => f.result.current.submit());
    expect(f.controller.save).toHaveBeenCalledWith("Confirmed");
    f.rerender({ state: { ...f.ready, stage: "saving" } });
    expect(f.navigation.getSnapshot().protection).toBe("pending");
    f.rerender({ state: { ...f.ready, stage: "saved", review: null } });
    expect(f.navigation.getSnapshot().protection).toBe("none");
    expect(f.result.current.reason.value).toBe("");
    f.unmount();
  });
});
