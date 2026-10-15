import { act, cleanup, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import { WorkspaceNavigationContext } from "../../../../core/presentation/navigation/use-navigation-protection";
import { WorkspaceNavigationController } from "../../../../core/presentation/navigation/workspace-navigation-controller";
import {
  batchAccess as access,
  batchOperation,
  batchReview as review,
} from "../../di/leave-batch-fixture";
import type { LeaveBatchKind } from "../../domain/entities/leave-batch";
import {
  initialLeaveBatchStartState,
  type LeaveBatchStartState,
} from "../models/leave-batch-state";
import { LeaveBatchStartController } from "./leave-batch-start-controller";
import { useLeaveBatchForm } from "./use-leave-batch-form";

afterEach(cleanup);
function fixture(kind: LeaveBatchKind = "ACCRUAL") {
  const navigation = new WorkspaceNavigationController();
  const controller = new LeaveBatchStartController(
    {
      reviewBatch: { execute: vi.fn().mockResolvedValue(failed("access_denied")) },
      startBatch: { execute: vi.fn().mockResolvedValue(failed("access_denied")) },
    },
    access,
    kind,
    () => batchOperation,
  );
  const wrapper = ({ children }: { children: ReactNode }) => (
    <WorkspaceNavigationContext value={navigation}>{children}</WorkspaceNavigationContext>
  );
  const state: LeaveBatchStartState = initialLeaveBatchStartState;
  const hook = renderHook(
    ({ state }) => useLeaveBatchForm(controller, state, access.companyId, kind, "2026-10-15"),
    { initialProps: { state }, wrapper },
  );
  return { ...hook, controller, navigation, state };
}
it("keeps company-owned type selection and submits an explicit month", async () => {
  const f = fixture(),
    prepare = vi.spyOn(f.controller, "prepare").mockResolvedValue();
  expect(f.result.current.period.value).toBe("2026-09");
  await act(async () => f.result.current.chooseType());
  expect(f.result.current.pickerOpen).toBe(true);
  await act(async () =>
    f.result.current.selectType({
      ...review.type,
      companyId: "10000000-0000-4000-8000-000000000099" as CompanyId,
    }),
  );
  expect(f.result.current.type).toBeNull();
  await act(async () => f.result.current.selectType(review.type));
  await act(async () => f.result.current.prepare());
  expect(prepare).toHaveBeenCalledWith({
    kind: "ACCRUAL",
    typeId: review.type.id,
    period: "2026-09",
  });
  expect(f.navigation.getSnapshot().protection).toBe("dirty");
  f.unmount();
  expect(f.navigation.getSnapshot().protection).toBe("none");
});
it("year-close input resolves to December without reusing an accrual month", async () => {
  const f = fixture("YEAR_CLOSE"),
    prepare = vi.spyOn(f.controller, "prepare").mockResolvedValue();
  expect(f.result.current.period.value).toBe("2025");
  await act(async () => f.result.current.selectType(review.type));
  await act(async () => f.result.current.prepare());
  expect(prepare).toHaveBeenCalledWith({
    kind: "YEAR_CLOSE",
    typeId: review.type.id,
    period: "2025-12",
  });
  f.unmount();
});
it("denied uncertain results erase private form values without releasing navigation protection", async () => {
  const f = fixture();
  await act(async () => {
    f.result.current.selectType(review.type);
    f.result.current.reason.onChange("Private reason");
  });
  await act(async () => f.rerender({ state: { ...f.state, stage: "reviewing", review } }));
  await act(async () => f.rerender({ state: { ...f.state, stage: "saving", review } }));
  expect(f.navigation.getSnapshot().protection).toBe("pending");
  await act(async () => f.result.current.chooseType());
  expect(f.result.current.pickerOpen).toBe(false);
  await act(async () => f.rerender({ state: { ...f.state, stage: "unconfirmed", review: null } }));
  expect(f.result.current.type).toBeNull();
  expect(f.result.current.period.value).toBe("");
  expect(f.result.current.reason.value).toBe("");
  expect(f.navigation.getSnapshot().protection).toBe("unconfirmed");
  f.unmount();
  expect(f.navigation.getSnapshot().protection).toBe("none");
});
it("reload preserves edits until departure is explicitly accepted", async () => {
  const f = fixture(),
    refresh = vi.spyOn(f.controller, "refresh").mockImplementation(() => {});
  await act(async () => f.result.current.selectType(review.type));
  await act(async () => f.result.current.reload());
  expect(f.navigation.getSnapshot().deciding).toBe(true);
  expect(refresh).not.toHaveBeenCalled();
  await act(async () => f.navigation.stay());
  expect(f.result.current.type).toEqual(review.type);
  await act(async () => f.result.current.reload());
  await act(async () => f.navigation.leave());
  expect(refresh).toHaveBeenCalledOnce();
  expect(f.result.current.type).toBeNull();
  expect(f.result.current.period.value).toBe("2026-09");
  f.unmount();
});
