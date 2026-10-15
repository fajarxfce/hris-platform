export type LeaveCarryExpiryResult = Readonly<{
  ordinal: number;
  employee: Readonly<{ id: string; number: string | null; name: string | null }>;
  jobId: string;
  status: "EXPIRED" | "UNCHANGED" | "NO_AWARD";
  expiredDays: string;
  ledgerId: string | null;
  recordedAt: string;
}>;
