import { act, cleanup, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import { WorkspaceNavigationContext } from "../../../../core/presentation/navigation/use-navigation-protection";
import { WorkspaceNavigationController } from "../../../../core/presentation/navigation/workspace-navigation-controller";
import { accrualReview as review } from "../../di/leave-accrual-schedule-fixture";
import { batchAccess as access, batchOperation, batchReview } from "../../di/leave-batch-fixture";
import {
  initialLeaveAccrualScheduleStartState,
  type LeaveAccrualScheduleStartState,
} from "../models/leave-accrual-schedule-state";
import { LeaveAccrualScheduleStartController } from "./leave-accrual-schedule-start-controller";
import { useLeaveAccrualScheduleForm } from "./use-leave-accrual-schedule-form";

afterEach(cleanup);
function fixture() {
  const navigation = new WorkspaceNavigationController();
  const controller = new LeaveAccrualScheduleStartController(
    {
      reviewAccrualSchedule: { execute: vi.fn().mockResolvedValue(failed("access_denied")) },
      scheduleAccrual: { execute: vi.fn().mockResolvedValue(failed("access_denied")) },
    },
    access,
    () => batchOperation,
  );
  const wrapper = ({ children }: { children: ReactNode }) => (
    <WorkspaceNavigationContext value={navigation}>{children}</WorkspaceNavigationContext>
  );
  const state: LeaveAccrualScheduleStartState = initialLeaveAccrualScheduleStartState;
  const hook = renderHook(
    ({ state }) => useLeaveAccrualScheduleForm(controller, state, access.companyId, "2026-10-15"),
    { initialProps: { state }, wrapper },
  );
  return { ...hook, controller, navigation, state };
}
it("submits an explicit bounded-period selection from a company-owned picker", async () => {
  const f = fixture(),
    prepare = vi.spyOn(f.controller, "prepare").mockResolvedValue();
  expect(f.result.current.firstPeriod.value).toBe("2026-10");
  expect(f.result.current.periods.value).toBe("3");
  await act(async () => f.result.current.chooseType());
  expect(f.result.current.pickerOpen).toBe(true);
  await act(async () =>
    f.result.current.selectType({
      ...batchReview.type,
      companyId: "10000000-0000-4000-8000-000000000099" as CompanyId,
    }),
  );
  expect(f.result.current.type).toBeNull();
  await act(async () => {
    f.result.current.selectType(batchReview.type);
    f.result.current.firstPeriod.onChange("2027-01");
    f.result.current.periods.onChange("12");
  });
  await act(async () => f.result.current.prepare());
  expect(prepare).toHaveBeenCalledWith({
    typeId: batchReview.type.id,
    firstPeriod: "2027-01",
    periods: 12,
  });
  expect(f.navigation.getSnapshot().protection).toBe("dirty");
  f.unmount();
  expect(f.navigation.getSnapshot().protection).toBe("none");
});
it("erases denied private fields while retaining protection of an unconfirmed command", async () => {
  const f = fixture();
  await act(async () => {
    f.result.current.selectType(batchReview.type);
    f.result.current.reason.onChange("Private reason");
  });
  await act(async () => f.rerender({ state: { ...f.state, stage: "reviewing", review } }));
  await act(async () => f.rerender({ state: { ...f.state, stage: "saving", review } }));
  expect(f.navigation.getSnapshot().protection).toBe("pending");
  await act(async () => f.result.current.chooseType());
  expect(f.result.current.pickerOpen).toBe(false);
  await act(async () => f.rerender({ state: { ...f.state, stage: "unconfirmed", review: null } }));
  expect(f.result.current.type).toBeNull();
  expect(f.result.current.reason.value).toBe("");
  expect(f.result.current.firstPeriod.value).toBe("");
  expect(f.result.current.periods.value).toBe("");
  expect(f.navigation.getSnapshot().protection).toBe("unconfirmed");
  f.unmount();
  expect(f.navigation.getSnapshot().protection).toBe("none");
});
it("restores freshly authorized review selection after MFA without restoring private text", async () => {
  const f = fixture();
  await act(async () => {
    f.result.current.selectType(batchReview.type);
    f.result.current.reason.onChange("Private reason");
  });
  await act(async () => f.rerender({ state: { ...f.state, stage: "unavailable" } }));
  expect(f.result.current.type).toBeNull();
  expect(f.result.current.reason.value).toBe("");
  await act(async () => f.rerender({ state: { ...f.state, stage: "reviewing", review } }));
  expect(f.result.current.type?.id).toBe(review.selection.typeId);
  expect(f.result.current.firstPeriod.value).toBe(review.selection.firstPeriod);
  expect(f.result.current.periods.value).toBe(String(review.selection.periods));
  expect(f.result.current.reason.value).toBe("");
  await act(async () => f.result.current.reason.onChange("New reason"));
  await act(async () =>
    f.rerender({ state: { ...f.state, stage: "reviewing", review: { ...review } } }),
  );
  expect(f.result.current.reason.value).toBe("New reason");
  f.unmount();
});
it("retains draft and selected periods until the user accepts reload", async () => {
  const f = fixture(),
    refresh = vi.spyOn(f.controller, "refresh").mockImplementation(() => {});
  await act(async () => {
    f.result.current.selectType(batchReview.type);
    f.result.current.periods.onChange("12");
  });
  await act(async () => f.result.current.reload());
  expect(f.navigation.getSnapshot().deciding).toBe(true);
  expect(refresh).not.toHaveBeenCalled();
  await act(async () => f.navigation.stay());
  expect(f.result.current.periods.value).toBe("12");
  await act(async () => f.result.current.reload());
  await act(async () => f.navigation.leave());
  expect(refresh).toHaveBeenCalledOnce();
  expect(f.result.current.type).toBeNull();
  expect(f.result.current.firstPeriod.value).toBe("2026-10");
  expect(f.result.current.periods.value).toBe("3");
  f.unmount();
});
it("confirmation clears draft values and departure protection", async () => {
  const f = fixture();
  await act(async () => {
    f.result.current.selectType(batchReview.type);
    f.result.current.reason.onChange("Reviewed plan");
  });
  await act(async () => f.rerender({ state: { ...f.state, stage: "saved" } }));
  expect(f.result.current.type).toBeNull();
  expect(f.result.current.reason.value).toBe("");
  expect(f.navigation.getSnapshot().protection).toBe("none");
  f.unmount();
});
