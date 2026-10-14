export type PayrollPeriodMember = Readonly<{
  employeeId: string;
  employeeNumber: string;
  name: string;
  inputVersion: number | null;
  inputStatus: "DRAFT" | "VERIFIED" | null;
}>;
