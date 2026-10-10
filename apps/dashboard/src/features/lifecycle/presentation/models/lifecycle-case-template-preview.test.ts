import { describe, expect, it } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  LifecycleTemplate,
  LifecycleTemplateId,
} from "../../domain/entities/lifecycle-template";
import { lifecycleCaseTemplatePreview } from "./lifecycle-case-template-preview";

const template: LifecycleTemplate = {
  id: "90000000-abcd-4000-8000-000000000001" as LifecycleTemplateId,
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  code: "ONBOARD",
  name: "Onboarding",
  kind: "ONBOARDING",
  active: true,
  version: 0,
  tasks: [
    { key: "before", title: "Prepare equipment", required: true, dueDays: -1 },
    { key: "after", title: "Review access", required: false, dueDays: 365 },
  ],
};
describe("checklist calendar preview", () => {
  it("uses calendar offsets across leap days without browser timezone conversion", () => {
    expect(lifecycleCaseTemplatePreview(template, "2028-03-01", "en")).toEqual([
      { id: "before", cells: ["Prepare equipment", "Required", "Feb 29, 2028"] },
      { id: "after", cells: ["Review access", "Optional", "Mar 1, 2029"] },
    ]);
    expect(lifecycleCaseTemplatePreview(template, "2028-03-01", "id")[0]?.cells).toEqual([
      "Prepare equipment",
      "Wajib",
      "29 Feb 2028",
    ]);
  });
  it("keeps the checklist visible while an empty or invalid target date is being edited", () => {
    for (const target of ["", "2026-02-29", "1899-12-31", "2201-01-01"])
      expect(
        lifecycleCaseTemplatePreview(template, target, "en").map((row) => row.cells[2]),
      ).toEqual(["—", "—"]);
  });
});
