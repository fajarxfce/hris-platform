import { expect, it } from "vitest";
import {
  onboardingDetails as details,
  onboardingReview as review,
} from "../../di/onboarding-schedule-fixture";
import {
  onboardingScheduleReviewView,
  onboardingScheduleStatus,
  onboardingScheduleView,
  onboardingTaskRows,
} from "./onboarding-schedule-view";

it("separates checklist initiation from task completion and stopped job recovery", () => {
  expect(onboardingScheduleStatus(details, "en")).toBe("Scheduled");
  expect(
    onboardingScheduleStatus(
      { ...details, job: { ...details.job, cancellationRequested: true } },
      "en",
    ),
  ).toBe("Cancelling");
  expect(
    onboardingScheduleStatus({ ...details, job: { ...details.job, status: "FAILED" } }, "en"),
  ).toBe("Needs recovery");
  expect(
    onboardingScheduleStatus(
      { ...details, status: "STARTED", job: { ...details.job, status: "SUCCEEDED" } },
      "en",
    ),
  ).toBe("Checklist created");
  expect(onboardingScheduleStatus({ ...details, status: "STARTED" }, "id")).toBe(
    "Checklist dibuat",
  );
});
it("formats task deadlines and does not read inherited object keys as assignees", () => {
  const template = {
    ...review.template,
    tasks: [{ key: "constructor", title: "Prepare equipment", required: true, dueDays: -1 }],
  };
  expect(onboardingTaskRows(template, "2026-10-02", {}, "en")[0]?.cells).toEqual([
    "Prepare equipment",
    "Required",
    "Oct 1, 2026",
    "Unassigned",
  ]);
  expect(
    onboardingTaskRows(template, "2026-10-02", { constructor: "Joining coordinator" }, "en")[0]
      ?.cells[3],
  ).toBe("Joining coordinator");
});
it("keeps current failures separate from retained evidence and localizes every blocker", () => {
  const view = onboardingScheduleView(
    { ...details, status: "FAILED", failureCode: "onboarding_terms_changed" },
    "en",
  );
  expect(view.issue?.code).toBe("onboarding_terms_changed");
  expect(view.tasks[0]?.cells.at(-1)).toBe("Assigned");
  expect(view.assignees[0]?.value).toBe(Object.values(details.assignees)[0]);
  expect(view.properties).toContainEqual({ label: "Company timezone", value: "Asia/Jakarta" });
  expect(
    onboardingScheduleReviewView({ ...review, blockers: ["onboarding_already_completed"] }, "id")
      .issues,
  ).toEqual([{ code: "onboarding_already_completed", fields: {}, parameters: {} }]);
});
