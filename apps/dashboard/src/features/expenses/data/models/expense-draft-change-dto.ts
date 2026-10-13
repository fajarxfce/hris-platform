import type { ExpenseLineDto } from "./expense-claim-dto";
export type ExpenseDraftChangeDto = Readonly<{
  employmentId: string;
  expectedVersion: number | null;
  title: string;
  description: string;
  lines: readonly ExpenseLineDto[];
  reason: string;
}>;
