export type ExpensePolicyTerms = Readonly<{
  name: string;
  maximumLineAmount: string;
  maximumClaimAmount: string;
  receiptRequired: boolean;
  costCenterRequired: boolean;
  maximumAgeDays: number;
  allowedContracts: readonly ("PERMANENT" | "FIXED_TERM")[];
}>;
