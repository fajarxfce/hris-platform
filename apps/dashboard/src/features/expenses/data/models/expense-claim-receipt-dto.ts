import { z } from "zod";
import { expenseVersionDto } from "./expense-claim-dto";
export const expenseClaimReceiptDto = z.object({ id: z.uuid(), version: expenseVersionDto });
export type ExpenseClaimReceiptDto = z.infer<typeof expenseClaimReceiptDto>;
