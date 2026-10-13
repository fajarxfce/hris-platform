export type ExpensePaymentPreparationDto = Readonly<{
  title: string;
  reason: string;
  items: readonly Readonly<{
    id: string;
    submissionId: string;
    destination: Readonly<{ bankCode: string; accountNumber: string; accountName: string }>;
  }>[];
}>;
