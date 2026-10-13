import type { ExpenseLine } from "./expense-line";
export type ExpenseDraft = Readonly<{
  revision: number;
  employeeNumber: string;
  employeeName: string;
  title: string;
  description: string;
  totalAmount: string;
  lines: readonly ExpenseLine[];
  actorId: string;
  reason: string;
  recordedAt: string;
}>;
