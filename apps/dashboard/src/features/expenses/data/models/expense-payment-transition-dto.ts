import { z } from "zod";
import { expenseVersionDto } from "./expense-claim-dto";
export type ExpensePaymentTransitionDto = Readonly<{ expectedVersion: number; reason: string }>;
export const expensePaymentTransitionReceiptDto = z.object({
  id: z.uuid(),
  version: expenseVersionDto,
});
export type ExpensePaymentTransitionReceiptDto = z.infer<typeof expensePaymentTransitionReceiptDto>;
