import type { ExpenseClaimId } from "./expense-claim";

type Fields = Readonly<{ claimId: ExpenseClaimId; version: number; reason: string }>;
export type ExpenseClaimActionCommand = Fields &
  (
    | Readonly<{ action: "SUBMIT"; submissionId: string }>
    | Readonly<{ action: "CANCEL" }>
    | Readonly<{ action: "WITHDRAW"; submissionId: string }>
  );
