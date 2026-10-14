import type { ptkpStatuses, taxResidencies } from "./payroll-tax-registration";
export type TaxOpeningHistory = Readonly<{
  taxableGross: string;
  retirementContributions: string;
  qualifiedDonations: string;
  withheld: string;
  employmentMonths: number;
  previousEmployerNet: string;
  previousEmployerWithheld: string;
}>;
export type TaxOpeningTerms = Readonly<{
  throughMonth: number;
  residency: (typeof taxResidencies)[number];
  ptkp: (typeof ptkpStatuses)[number];
  history: TaxOpeningHistory;
  reference: string;
}>;
