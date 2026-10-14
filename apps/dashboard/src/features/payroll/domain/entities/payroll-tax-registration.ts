export const taxResidencies = ["RESIDENT", "NON_RESIDENT"] as const;
export const ptkpStatuses = ["TK0", "TK1", "TK2", "TK3", "K0", "K1", "K2", "K3"] as const;
export type PayrollTaxRegistration = Readonly<{
  residency: (typeof taxResidencies)[number];
  ptkp: (typeof ptkpStatuses)[number];
  residenceCountry: string;
  subjectiveFrom: string | null;
  subjectiveUntil: string | null;
  verifiedOn: string;
  verificationReference: string;
}>;
