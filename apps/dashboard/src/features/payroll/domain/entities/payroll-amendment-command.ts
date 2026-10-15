export type PayrollAmendmentDraftAction = "CALCULATE" | "CANCEL";

export type PayrollAmendmentCommand = Readonly<{
  expectedVersion: number;
  reason: string;
}>;
