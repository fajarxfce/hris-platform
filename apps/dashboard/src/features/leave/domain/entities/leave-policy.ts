export type LeavePolicy = Readonly<{
  typeId: string;
  code: string;
  revision: number;
  name: string;
  paid: boolean;
  allowPartialDays: boolean;
  minServiceMonths: number;
  allowedContracts: readonly ("PERMANENT" | "FIXED_TERM")[];
  maxRequestDays: number;
  attachmentRequired: boolean;
}>;
