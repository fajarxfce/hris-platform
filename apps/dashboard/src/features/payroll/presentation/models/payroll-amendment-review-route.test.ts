import { describe, expect, it } from "vitest";
import {
  payrollAmendmentReviewActionPath,
  payrollAmendmentReviewPath,
  payrollAmendmentReviewSubmissionPath,
  payrollAmendmentReviewsPath,
} from "./payroll-amendment-review-route";

describe("amendment review navigation context", () => {
  it("preserves directory filters while removing a proposal's selected evidence", () => {
    const original = new URLSearchParams({
      company: "old",
      year: "2026",
      query: "Employee & Co",
      revision: "8",
      historyAfter: "4",
      assessment: "private",
      tab: "calculation",
    });
    for (const path of [
      payrollAmendmentReviewsPath("current", "amendment", original),
      payrollAmendmentReviewSubmissionPath("current", "amendment", original),
    ]) {
      const target = new URL(path, "https://example.invalid");
      expect(Object.fromEntries(target.searchParams)).toEqual({
        company: "current",
        year: "2026",
        query: "Employee & Co",
      });
    }
    expect(original.get("company")).toBe("old");
    expect(original.get("revision")).toBe("8");
  });
  it("links the review resource independently and removes detail tabs before a command", () => {
    const parameters = new URLSearchParams({
        company: "old",
        tab: "stages",
        revision: "2",
        year: "2026",
      }),
      detail = new URL(
        payrollAmendmentReviewPath("company", "review/id", parameters),
        "https://example.invalid",
      );
    expect(detail.pathname).toBe("/payroll/amendment-reviews/review%2Fid");
    expect(detail.searchParams.get("tab")).toBe("stages");
    expect(detail.searchParams.has("revision")).toBe(false);
    for (const action of ["APPROVE", "REJECT", "WITHDRAW"] as const) {
      const command = new URL(
        payrollAmendmentReviewActionPath("company", "review", action, parameters),
        "https://example.invalid",
      );
      expect(command.pathname).toBe(`/payroll/amendment-reviews/review/${action.toLowerCase()}`);
      expect(Object.fromEntries(command.searchParams)).toEqual({
        company: "company",
        year: "2026",
      });
    }
  });
});
