export type ExpenseSubmissionId = string & { readonly expenseSubmissionId: unique symbol };
export type ExpenseSubmissionSummary = Readonly<{
  id: ExpenseSubmissionId;
  number: number;
  draftRevision: number;
  approvalId: string;
  title: string;
  totalAmount: string;
  submittedBy: string;
  submittedAt: string;
}>;
