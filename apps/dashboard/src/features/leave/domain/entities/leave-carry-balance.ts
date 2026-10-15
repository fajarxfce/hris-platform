export type LeaveCarryBalance = Readonly<{
  closingId: string;
  awardedDays: string;
  availableDays: string;
  reservedDays: string;
  consumedDays: string;
  expiredDays: string;
  transferredDays: string;
  deductedDays: string;
  validThrough: string | null;
  expiresAt: string | null;
  timezone: string;
  version: number;
}>;
