export type PayrollReviewTotals = Readonly<{
  employeeCount: number;
  taxableGross: string;
  withheld: string;
  takeHome: string;
  currency: "IDR";
}>;
