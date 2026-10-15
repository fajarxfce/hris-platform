import { act, cleanup, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import { WorkspaceNavigationContext } from "../../../../core/presentation/navigation/use-navigation-protection";
import { WorkspaceNavigationController } from "../../../../core/presentation/navigation/workspace-navigation-controller";
import {
  onboardingAccess as access,
  onboardingAccount as account,
  onboardingEmployeeDetails as employee,
  onboardingReview as review,
} from "../../di/onboarding-schedule-fixture";
import {
  initialOnboardingScheduleStartState,
  type OnboardingScheduleStartState,
} from "../models/onboarding-schedule-state";
import { OnboardingScheduleStartController } from "./onboarding-schedule-start-controller";
import { useOnboardingScheduleForm } from "./use-onboarding-schedule-form";

afterEach(cleanup);
function fixture() {
  const navigation = new WorkspaceNavigationController();
  const controller = new OnboardingScheduleStartController(
    { execute: vi.fn().mockResolvedValue(failed("access_denied")) },
    {
      loadOnboardingScheduleReview: { execute: vi.fn().mockResolvedValue(failed("access_denied")) },
      scheduleOnboarding: { execute: vi.fn().mockResolvedValue(failed("access_denied")) },
    },
    access,
    employee.id,
    "2026-10-02",
    () => account,
  );
  const wrapper = ({ children }: { children: ReactNode }) => (
    <WorkspaceNavigationContext value={navigation}>{children}</WorkspaceNavigationContext>
  );
  const state: OnboardingScheduleStartState = {
    ...initialOnboardingScheduleStartState,
    stage: "editing",
    employee,
  };
  const hook = renderHook(({ state }) => useOnboardingScheduleForm(controller, state), {
    initialProps: { state },
    wrapper,
  });
  return { ...hook, controller, navigation, state };
}
it("starts with the employment date and carries explicit template selection into preparation", async () => {
  const f = fixture(),
    prepare = vi.spyOn(f.controller, "prepare").mockResolvedValue();
  expect(f.result.current.startsOn.value).toBe(employee.terms.startDate);
  await act(async () => {
    f.result.current.chooseTemplate();
  });
  expect(f.result.current.picker).toEqual({ kind: "template" });
  await act(async () => {
    f.result.current.selectTemplate({
      ...review.template,
      companyId: "10000000-0000-4000-8000-000000000099" as CompanyId,
    });
  });
  expect(f.result.current.template).toBeNull();
  await act(async () => {
    f.result.current.selectTemplate(review.template);
    f.result.current.startsOn.onChange(review.startsOn);
  });
  await act(async () => f.result.current.prepare());
  expect(prepare).toHaveBeenCalledWith(review.template, review.startsOn);
  expect(f.navigation.getSnapshot().protection).toBe("dirty");
  f.unmount();
  expect(f.navigation.getSnapshot().protection).toBe("none");
});
it("assigns and clears a task, resets selections on new review and prevents cross-company assignment", async () => {
  const f = fixture();
  await act(async () => f.rerender({ state: { ...f.state, stage: "reviewing", review } }));
  await act(async () => f.result.current.chooseAssignee("equipment"));
  await act(async () =>
    f.result.current.selectAssignee({
      id: account,
      companyId: "10000000-0000-4000-8000-000000000099" as CompanyId,
      displayName: "Other company",
    }),
  );
  expect(f.result.current.assignees).toEqual({});
  await act(async () => f.result.current.chooseAssignee("equipment"));
  await act(async () =>
    f.result.current.selectAssignee({
      id: account,
      companyId: access.companyId,
      displayName: "Joining coordinator",
    }),
  );
  expect(f.result.current.assignees.equipment?.id).toBe(account);
  await act(async () => f.result.current.chooseAssignee("equipment"));
  await act(async () => f.result.current.clearAssignee());
  expect(f.result.current.assignees).toEqual({});
  await act(async () => f.result.current.chooseAssignee("equipment"));
  await act(async () =>
    f.result.current.selectAssignee({
      id: account,
      companyId: access.companyId,
      displayName: "Joining coordinator",
    }),
  );
  await act(async () => f.rerender({ state: f.state }));
  expect(f.result.current.assignees).toEqual({});
  expect(f.result.current.picker).toBeNull();
  f.unmount();
});
it("clears private input after denied access but retains uncertain navigation protection", async () => {
  const f = fixture();
  await act(async () => {
    f.result.current.selectTemplate(review.template);
    f.result.current.reason.onChange("Joining plan");
  });
  await act(async () => f.rerender({ state: { ...f.state, stage: "reviewing", review } }));
  await act(async () => f.result.current.chooseAssignee("equipment"));
  await act(async () =>
    f.result.current.selectAssignee({
      id: account,
      companyId: access.companyId,
      displayName: "Joining coordinator",
    }),
  );
  await act(async () => f.rerender({ state: { ...f.state, stage: "saving", review } }));
  expect(f.navigation.getSnapshot().protection).toBe("pending");
  await act(async () => {
    f.result.current.chooseTemplate();
    f.result.current.chooseAssignee("welcome");
  });
  expect(f.result.current.picker).toBeNull();
  await act(async () =>
    f.rerender({
      state: {
        ...initialOnboardingScheduleStartState,
        stage: "unconfirmed",
        failure: { code: "mfa_required", fields: {}, parameters: {} },
      },
    }),
  );
  expect(f.result.current.template).toBeNull();
  expect(f.result.current.assignees).toEqual({});
  expect(f.result.current.reason.value).toBe("");
  expect(f.result.current.startsOn.value).toBe("");
  expect(f.navigation.getSnapshot().protection).toBe("unconfirmed");
  f.unmount();
  expect(f.navigation.getSnapshot().protection).toBe("none");
});
it("reload respects unsaved edits and clears them only after the navigation decision", async () => {
  const f = fixture(),
    refresh = vi.spyOn(f.controller, "refresh").mockResolvedValue();
  await act(async () => f.result.current.selectTemplate(review.template));
  await act(async () => f.result.current.reload());
  expect(f.navigation.getSnapshot().deciding).toBe(true);
  expect(refresh).not.toHaveBeenCalled();
  await act(async () => f.navigation.stay());
  expect(f.result.current.template).toEqual(review.template);
  await act(async () => f.result.current.reload());
  await act(async () => f.navigation.leave());
  expect(refresh).toHaveBeenCalledOnce();
  expect(f.result.current.template).toBeNull();
  expect(f.result.current.reason.value).toBe("");
  f.unmount();
});
