/** One coherent payment balance, independent of a separately acquired payslip. */
export type PayrollSettlement = Readonly<{
  assessmentRevision: number;
  entitlement: string;
  confirmedPaid: string;
  confirmedRecovered: string;
  payable: string;
  recoverable: string;
  currency: "IDR";
}>;
