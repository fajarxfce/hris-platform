export type ExpenseLine = Readonly<{
  id: string;
  categoryId: string;
  occurredOn: string;
  amount: string;
  description: string;
  costCenterId: string | null;
  receiptRevisionIds: readonly string[];
}>;
