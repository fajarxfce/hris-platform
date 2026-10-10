import { act, cleanup, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { useController } from "react-hook-form";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import { WorkspaceNavigationContext } from "../../../../core/presentation/navigation/use-navigation-protection";
import { WorkspaceNavigationController } from "../../../../core/presentation/navigation/workspace-navigation-controller";
import type {
  LifecycleTemplate,
  LifecycleTemplateId,
} from "../../domain/entities/lifecycle-template";
import {
  initialLifecycleTemplateEditorState,
  type LifecycleTemplateEditorState,
} from "../models/lifecycle-template-editor-state";
import {
  lifecycleTemplateFields,
  lifecycleTemplateFormValues,
} from "../models/lifecycle-template-form-values";
import { LifecycleTemplateEditorController } from "./lifecycle-template-editor-controller";
import { useLifecycleTemplateForm } from "./use-lifecycle-template-form";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "90000000-abcd-4000-8000-000000000001" as LifecycleTemplateId;
const template = (): LifecycleTemplate => ({
  id,
  companyId,
  code: "ONBOARD",
  name: "Onboarding",
  kind: "ONBOARDING",
  active: true,
  version: 2,
  tasks: [{ key: "equipment", title: "Equipment", required: true, dueDays: -2 }],
});
function fixture() {
  const navigation = new WorkspaceNavigationController();
  const controller = new LifecycleTemplateEditorController(
    {
      loadTemplate: { execute: vi.fn().mockResolvedValue(failed("access_denied")) },
      saveTemplate: { execute: vi.fn().mockResolvedValue(failed("access_denied")) },
    },
    { companyId, permissions: ["people.lifecycle.manage"] },
    false,
    id,
    () => id,
  );
  const wrapper = ({ children }: { children: ReactNode }) => (
    <WorkspaceNavigationContext value={navigation}>{children}</WorkspaceNavigationContext>
  );
  const state: LifecycleTemplateEditorState = {
    ...initialLifecycleTemplateEditorState,
    stage: "editing",
    template: template(),
  };
  return { navigation, controller, wrapper, state };
}
afterEach(cleanup);
describe("lifecycle checklist form ownership", () => {
  it("preserves negative due offsets and rejects an empty number instead of treating it as zero", () => {
    const values = lifecycleTemplateFormValues(template());
    expect(values.tasks[0]?.dueDays).toBe("-2");
    expect(values.reason).toBe("");
    expect(
      lifecycleTemplateFields({
        ...values,
        tasks: [{ key: "equipment", title: "Equipment", required: false, dueDays: " " }],
      }).tasks[0]?.dueDays,
    ).toBeNaN();
  });
  it("retains controlled task edits through verification and resets an identical fresh snapshot only on reload", async () => {
    const f = fixture();
    const refresh = vi.spyOn(f.controller, "refresh").mockResolvedValue();
    const hook = renderHook(
      ({ state }) => {
        const form = useLifecycleTemplateForm(f.controller, state);
        const task = useController({ name: "tasks.0.title", control: form.control });
        return { form, task: task.field };
      },
      { initialProps: { state: f.state }, wrapper: f.wrapper },
    );
    await act(async () => {
      hook.result.current.form.reason.onChange("Retain this draft");
      hook.result.current.task.onChange("Review access");
    });
    expect(f.navigation.getSnapshot().protection).toBe("dirty");
    await act(async () =>
      hook.rerender({
        state: { ...f.state, failure: { code: "mfa_required", fields: {}, parameters: {} } },
      }),
    );
    expect(hook.result.current.form.reason.value).toBe("Retain this draft");
    expect(hook.result.current.task.value).toBe("Review access");
    await act(async () => hook.result.current.form.refresh());
    expect(f.navigation.getSnapshot().deciding).toBe(true);
    expect(refresh).not.toHaveBeenCalled();
    await act(async () => f.navigation.leave());
    expect(refresh).toHaveBeenCalledOnce();
    await act(async () => hook.rerender({ state: { ...f.state, template: template() } }));
    expect(hook.result.current.form.reason.value).toBe("");
    expect(hook.result.current.task.value).toBe("Equipment");
    expect(f.navigation.getSnapshot().protection).toBe("none");
    hook.unmount();
    expect(f.navigation.getSnapshot()).toEqual({ protection: "none", deciding: false });
  });
  it("bounds dynamic task rows and freezes array actions while a command is unconfirmed", async () => {
    const f = fixture();
    const hook = renderHook(({ state }) => useLifecycleTemplateForm(f.controller, state), {
      initialProps: { state: f.state },
      wrapper: f.wrapper,
    });
    const first = hook.result.current.tasks[0]?.id;
    await act(async () => hook.result.current.removeTask(0));
    expect(hook.result.current.tasks).toHaveLength(1);
    await act(async () => {
      for (let index = 0; index < 100; index++) hook.result.current.addTask();
    });
    expect(hook.result.current.tasks).toHaveLength(64);
    expect(new Set(hook.result.current.tasks.map((task) => task.id)).size).toBe(64);
    expect(hook.result.current.tasks[0]?.id).toBe(first);
    await act(async () => hook.result.current.addTask());
    expect(hook.result.current.tasks).toHaveLength(64);
    await act(async () => hook.rerender({ state: { ...f.state, stage: "unconfirmed" } }));
    await act(async () => {
      hook.result.current.addTask();
      hook.result.current.removeTask(0);
    });
    expect(hook.result.current.tasks).toHaveLength(64);
    expect(hook.result.current.editable).toBe(false);
    expect(f.navigation.getSnapshot().protection).toBe("unconfirmed");
    await act(async () => hook.rerender({ state: f.state }));
    await act(async () => {
      for (let index = 0; index < 100; index++) hook.result.current.removeTask(0);
    });
    expect(hook.result.current.tasks).toHaveLength(1);
    hook.unmount();
    expect(f.navigation.getSnapshot().protection).toBe("none");
  });
});
