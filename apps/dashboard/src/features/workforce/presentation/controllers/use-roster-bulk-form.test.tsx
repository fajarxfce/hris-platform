import { act, cleanup, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { success } from "../../../../core/domain/result";
import { WorkspaceNavigationContext } from "../../../../core/presentation/navigation/use-navigation-protection";
import { WorkspaceNavigationController } from "../../../../core/presentation/navigation/workspace-navigation-controller";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import { access, company, identifier, operation } from "../../di/calendar-fixture";
import { bulkReviewFixture } from "../../di/roster-bulk-fixture";
import { initialRosterBulkState, type RosterBulkState } from "../models/roster-bulk-state";
import { RosterBulkController } from "./roster-bulk-controller";
import { useRosterBulkForm } from "./use-roster-bulk-form";

afterEach(cleanup);
function fixture() {
  const navigation = new WorkspaceNavigationController();
  const controller = new RosterBulkController(
    {
      reviewRosterBulk: { execute: vi.fn().mockResolvedValue(success(bulkReviewFixture())) },
      applyRosterBulk: {
        execute: vi.fn().mockResolvedValue(success({ id: operation, version: 0 })),
      },
    },
    access,
    () => operation,
  );
  const wrapper = ({ children }: { children: ReactNode }) => (
    <WorkspaceNavigationContext value={navigation}>{children}</WorkspaceNavigationContext>
  );
  const state: RosterBulkState = initialRosterBulkState;
  const hook = renderHook(
    ({ state }) => useRosterBulkForm(controller, state, company, "2026-10-12"),
    { wrapper, initialProps: { state } },
  );
  return { ...hook, controller, navigation, state };
}
const employee = bulkReviewFixture().employees[0]?.employee;
if (!employee) throw new Error("Missing employee");
const selectedEmployee = employee;
const cohort = Array.from({ length: 50 }, (_, index) => ({
  ...selectedEmployee,
  id: identifier(index + 600) as EmployeeId,
  legalName: `Employee ${index + 1}`,
}));

describe("bulk roster editing controls", () => {
  it("retains explicit employee selection across pages and rejects another company's values", async () => {
    const f = fixture();
    await act(async () => f.result.current.togglePage(cohort.slice(0, 2), true));
    await act(async () => f.result.current.togglePage(cohort.slice(2, 4), true));
    expect(f.result.current.employees).toHaveLength(4);
    await act(async () =>
      f.result.current.toggleEmployee(
        { ...selectedEmployee, companyId: identifier(888) as CompanyId },
        true,
      ),
    );
    expect(f.result.current.employees).toHaveLength(4);
    await act(async () => f.result.current.togglePage(cohort.slice(0, 2), false));
    expect(f.result.current.employees.map((value) => value.id)).toEqual(
      cohort.slice(2, 4).map((value) => value.id),
    );
    expect(f.navigation.getSnapshot().protection).toBe("dirty");
    f.unmount();
    expect(f.navigation.getSnapshot().protection).toBe("none");
  });

  it("refuses a selection above 50 without silently trimming it and updates the date capacity", async () => {
    const f = fixture();
    await act(async () => {
      f.result.current.until.onChange("2026-10-15");
      f.result.current.togglePage(cohort, true);
    });
    expect(f.result.current.cellCount).toBe(200);
    await act(async () => f.result.current.toggleEmployee(selectedEmployee, true));
    expect(f.result.current.employees).toHaveLength(50);
    expect(f.result.current.employeeLimit).toBe(true);
    await act(async () => f.result.current.until.onChange("2026-10-16"));
    expect(f.result.current.cellCount).toBe(250);
    await act(async () => f.result.current.clearEmployees());
    expect(f.result.current.cellCount).toBe(0);
    expect(f.result.current.employeeLimit).toBe(false);
  });

  it("reviews without a reason but requires a reason only before committing the reviewed selection", async () => {
    const f = fixture();
    const prepare = vi.spyOn(f.controller, "prepare").mockResolvedValue();
    const save = vi.spyOn(f.controller, "save").mockResolvedValue();
    await act(async () => f.result.current.prepare());
    expect(prepare).not.toHaveBeenCalled();
    expect(f.result.current.invalidEmployees).toBe(true);
    await act(async () => {
      f.result.current.toggleEmployee(selectedEmployee, true);
      f.result.current.changeWeekdays(["MONDAY", "FRIDAY"]);
    });
    await act(async () => f.result.current.prepare());
    expect(prepare).toHaveBeenCalledExactlyOnceWith({
      employeeIds: [selectedEmployee.id],
      dates: ["2026-10-12", "2026-10-16"],
      mode: "DAY_OFF",
      shift: null,
    });
    await act(async () =>
      f.rerender({ state: { ...f.state, stage: "reviewing", review: bulkReviewFixture() } }),
    );
    await act(async () => f.result.current.submit());
    expect(save).not.toHaveBeenCalled();
    expect(f.result.current.invalidReason).toBe(true);
    await act(async () => f.result.current.reason.onChange("Reviewed coverage"));
    await act(async () => f.result.current.submit());
    expect(save).toHaveBeenCalledExactlyOnceWith("Reviewed coverage");
  });

  it("keeps field input after a version conflict and clears private input after access loss", async () => {
    const f = fixture();
    await act(async () => {
      f.result.current.toggleEmployee(selectedEmployee, true);
      f.result.current.reason.onChange("Coverage");
    });
    await act(async () =>
      f.rerender({ state: { ...f.state, stage: "conflict", review: bulkReviewFixture() } }),
    );
    expect(f.result.current.employees).toHaveLength(1);
    expect(f.result.current.reason.value).toBe("Coverage");
    await act(async () =>
      f.rerender({ state: { ...f.state, stage: "saving", review: bulkReviewFixture() } }),
    );
    expect(f.navigation.getSnapshot().protection).toBe("pending");
    await act(async () => f.result.current.clearEmployees());
    expect(f.result.current.employees).toHaveLength(1);
    await act(async () =>
      f.rerender({
        state: {
          ...f.state,
          stage: "unconfirmed",
          failure: { code: "mfa_required", fields: {}, parameters: {} },
        },
      }),
    );
    expect(f.result.current.employees).toHaveLength(0);
    expect(f.result.current.reason.value).toBe("");
    expect(f.navigation.getSnapshot().protection).toBe("unconfirmed");
    await act(async () => f.rerender({ state: { ...f.state, stage: "saved" } }));
    expect(f.navigation.getSnapshot().protection).toBe("none");
  });

  it("asks the workspace to resolve pending edits before clearing the form on reload", async () => {
    const f = fixture();
    const refresh = vi.spyOn(f.controller, "refresh").mockImplementation(() => {});
    await act(async () => f.result.current.toggleEmployee(selectedEmployee, true));
    await act(async () => f.result.current.reload());
    expect(f.navigation.getSnapshot().deciding).toBe(true);
    expect(refresh).not.toHaveBeenCalled();
    await act(async () => f.navigation.stay());
    expect(f.result.current.employees).toHaveLength(1);
    await act(async () => f.result.current.reload());
    await act(async () => f.navigation.leave());
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(f.result.current.employees).toHaveLength(0);
  });
});
