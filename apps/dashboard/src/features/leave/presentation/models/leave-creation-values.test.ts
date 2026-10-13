import { expect, it } from "vitest";
import { type LeaveCreationValues, selectedLeaveDays } from "./leave-creation-values";

const input: LeaveCreationValues = {
  from: "2024-02-28",
  until: "2024-03-01",
  firstPortion: "SECOND_HALF",
  lastPortion: "FIRST_HALF",
  reason: "Personal leave",
};
it("keeps full intermediate days and the explicitly selected portions across leap day", () => {
  expect(selectedLeaveDays(input)).toEqual({
    ok: true,
    value: [
      { workDate: "2024-02-28", portion: "SECOND_HALF" },
      { workDate: "2024-02-29", portion: "FULL" },
      { workDate: "2024-03-01", portion: "FIRST_HALF" },
    ],
  });
});
it("uses one day length for a single-day selection", () => {
  expect(selectedLeaveDays({ ...input, until: input.from })).toEqual({
    ok: true,
    value: [{ workDate: input.from, portion: "SECOND_HALF" }],
  });
});
it.each([
  { ...input, from: "2026-02-30" },
  { ...input, until: "2024-01-01" },
  { ...input, until: "2026-01-01" },
  { ...input, firstPortion: "OTHER" },
])("rejects invalid or unbounded calendar input", (value) => {
  expect(selectedLeaveDays(value)).toMatchObject({ ok: false });
});
