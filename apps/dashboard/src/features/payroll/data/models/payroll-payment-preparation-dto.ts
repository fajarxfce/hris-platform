export type PayrollPaymentPreparationDto = Readonly<{
  title: string;
  reason: string;
  items: readonly Readonly<{
    id: string;
    assessmentId: string;
    assessmentRevision: number;
    paymentVersion: number;
    amount: string;
    destination: Readonly<{ bankCode: string; accountNumber: string; accountName: string }>;
  }>[];
}>;
