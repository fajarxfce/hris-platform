export type LeaveBalance = Readonly<{
  accountId: string | null;
  year: number;
  availableDays: string;
  reservedDays: string;
  consumedDays: string;
  version: number;
  closed: boolean;
}>;
