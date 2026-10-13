export type ExpensePolicyChangeDto = Readonly<{
  code: string;
  name: string;
  effectiveFrom: string;
  maximumLineAmount: string;
  maximumClaimAmount: string;
  receiptRequired: boolean;
  costCenterRequired: boolean;
  maximumAgeDays: number;
  allowedContracts: readonly string[];
  active: boolean;
  expectedVersion: number | null;
  reason: string;
}>;
