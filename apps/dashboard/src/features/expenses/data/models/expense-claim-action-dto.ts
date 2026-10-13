export type ExpenseClaimActionDto = Readonly<{ expectedVersion: number; reason: string }>;
export type ExpenseClaimSubmissionDto = ExpenseClaimActionDto & Readonly<{ submissionId: string }>;
