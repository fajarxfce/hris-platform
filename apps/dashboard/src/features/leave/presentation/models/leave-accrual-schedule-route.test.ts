import { describe, expect, it } from "vitest";
import { accrualScheduleId as schedule } from "../../di/leave-accrual-schedule-fixture";
import { batchCompany, batchId } from "../../di/leave-batch-fixture";
import {
  leaveAccrualSchedulePeriodLink,
  leaveAccrualScheduleRoute,
} from "./leave-accrual-schedule-route";
import { leaveBatchRoute } from "./leave-batch-route";

describe("company-owned schedule navigation", () => {
  it("clears a former company's selection and history cursor", () => {
    const route = leaveAccrualScheduleRoute(
      batchCompany,
      new URLSearchParams({ company: "former", after: schedule }),
      `/${schedule}`,
    );
    expect(route.foreignCompany).toBe(true);
    expect(route.after).toBeNull();
    expect(route.listTo).toBe(`/leave/accrual-schedules?company=${batchCompany}`);
  });
  it("retains history through creation and detail navigation", () => {
    const route = leaveAccrualScheduleRoute(
      batchCompany,
      new URLSearchParams({ company: batchCompany, after: schedule }),
      `/${schedule}`,
    );
    expect(route.after).toBe(schedule);
    expect(route.current).toContain(`/${schedule}?`);
    expect(route.createTo).toContain(`after=${schedule}`);
    expect(leaveAccrualScheduleRoute(batchCompany, new URLSearchParams()).canonical).toBe(false);
  });
  it("returns from period outcomes and recovery to the originating schedule", () => {
    const link = leaveAccrualSchedulePeriodLink(batchCompany, schedule, batchId, schedule);
    const parameters = new URLSearchParams(link.split("?")[1]);
    parameters.set("resultAfter", "20");
    const route = leaveBatchRoute(batchCompany, parameters, `/${batchId}`);
    expect(route.backTo).toBe(
      `/leave/accrual-schedules/${schedule}?company=${batchCompany}&after=${schedule}`,
    );
    expect(route.detailQuery).toContain(`schedule=${schedule}`);
    expect(route.detailQuery).toContain("resultAfter=20");
    expect(route.resultQuery).toContain(`scheduleAfter=${schedule}`);
    expect(route.resultQuery).not.toContain("resultAfter");
    expect(route.listTo).not.toContain("schedule");
    const resumed = leaveBatchRoute(
      batchCompany,
      new URLSearchParams(route.detailQuery),
      `/${batchId}/resume`,
    );
    expect(resumed.backTo).toBe(route.backTo);
  });
  it.each(["https://invalid.example", "../another", "", "wrong-id"])(
    "drops invalid schedule return target %s",
    (schedule) => {
      const route = leaveBatchRoute(
        batchCompany,
        new URLSearchParams({ company: batchCompany, schedule, scheduleAfter: batchId }),
        `/${batchId}`,
      );
      expect(route.schedule).toBeNull();
      expect(route.backTo).toBe(route.listTo);
      expect(route.detailQuery).not.toContain("schedule");
    },
  );
  it("clears schedule ownership on company replacement or independent batch creation", () => {
    const query = new URLSearchParams({
      company: "foreign",
      schedule,
      scheduleAfter: schedule,
      resultAfter: "20",
    });
    const route = leaveBatchRoute(batchCompany, query, `/${batchId}`);
    expect(route.schedule).toBeNull();
    expect(route.backTo).toBe(route.listTo);
    expect(route.detailQuery).not.toContain("schedule");
    query.set("company", batchCompany);
    expect(leaveBatchRoute(batchCompany, query, "/new").schedule).toBeNull();
  });
  it("ignores invalid schedule cursors and unrelated batch directory cursors", () => {
    const route = leaveBatchRoute(
      batchCompany,
      new URLSearchParams({
        company: batchCompany,
        schedule,
        scheduleAfter: "invalid",
        after: batchId,
      }),
      `/${batchId}`,
    );
    expect(route.backTo).toBe(`/leave/accrual-schedules/${schedule}?company=${batchCompany}`);
    expect(route.detailQuery).not.toContain("after=");
    expect(route.detailQuery).not.toContain("scheduleAfter");
  });
});
