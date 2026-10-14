import { assert, describe, expect, it } from "vitest";
import {
  compensationCompany,
  compensationEmployee,
  compensationPage,
  compensationRecord,
} from "../../../../../tests/fixtures/compensations";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { toCompensationReview } from "../../data/mappers/compensation-mapper";
import { compensationFields, compensationFormValues } from "./compensation-form-values";

describe("compensation form inputs", () => {
  it("requires explicit tax and risk choices for initial setup and preserves an absent optional basis", () => {
    const values = compensationFormValues(null, "2026-06");
    expect(values.basicSalary).toBe("");
    expect(values.residency).toBe("");
    expect(values.accidentRisk).toBe("");
    expect(compensationFields(values).ok).toBe(false);
    const review = toCompensationReview(
      compensationPage(compensationRecord()),
      compensationCompany as CompanyId,
      compensationEmployee,
      "2026-06",
      null,
    );
    const context = { employee: review.employee, current: review.current };
    const ready = compensationFormValues(context, "2026-06");
    ready.payBasisEnabled = false;
    const fields = compensationFields(ready);
    assert(fields.ok);
    expect(fields.value.terms.payBasis).toBeNull();
  });
});
