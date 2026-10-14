import type { TaxOpeningReview } from "../../domain/entities/tax-opening-review";
import type { TaxOpeningTerms } from "../../domain/entities/tax-opening-terms";
import type { TaxOpeningFields } from "../controllers/tax-opening-editor-controller";
export type TaxOpeningFormValues = {
  throughMonth: string;
  residency: string;
  ptkp: string;
  reference: string;
  reason: string;
  employmentMonths: string;
  taxableGross: string;
  retirementContributions: string;
  qualifiedDonations: string;
  withheld: string;
  previousEmployerNet: string;
  previousEmployerWithheld: string;
};
export function taxOpeningFormValues(review: TaxOpeningReview | null): TaxOpeningFormValues {
  const t = review?.current?.terms,
    h = t?.history;
  return {
    throughMonth: t ? String(t.throughMonth) : "",
    residency: t?.residency ?? "",
    ptkp: t?.ptkp ?? "",
    reference: t?.reference ?? "",
    reason: "",
    employmentMonths: h ? String(h.employmentMonths) : "",
    taxableGross: h?.taxableGross ?? "",
    retirementContributions: h?.retirementContributions ?? "",
    qualifiedDonations: h?.qualifiedDonations ?? "",
    withheld: h?.withheld ?? "",
    previousEmployerNet: h?.previousEmployerNet ?? "",
    previousEmployerWithheld: h?.previousEmployerWithheld ?? "",
  };
}
export function taxOpeningFields(values: TaxOpeningFormValues): TaxOpeningFields {
  return {
    reason: values.reason,
    terms: {
      throughMonth: /^(?:0|[1-9]\d*)$/u.test(values.throughMonth)
        ? Number(values.throughMonth)
        : Number.NaN,
      residency: values.residency as TaxOpeningTerms["residency"],
      ptkp: values.ptkp as TaxOpeningTerms["ptkp"],
      reference: values.reference,
      history: {
        taxableGross: values.taxableGross,
        retirementContributions: values.retirementContributions,
        qualifiedDonations: values.qualifiedDonations,
        withheld: values.withheld,
        employmentMonths: /^(?:0|[1-9]\d*)$/u.test(values.employmentMonths)
          ? Number(values.employmentMonths)
          : Number.NaN,
        previousEmployerNet: values.previousEmployerNet,
        previousEmployerWithheld: values.previousEmployerWithheld,
      },
    },
  };
}
