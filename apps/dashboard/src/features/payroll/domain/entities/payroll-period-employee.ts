export type PayrollPeriodEmployee = Readonly<{
  id: string;
  employeeNumber: string;
  name: string;
  employmentVersion: number;
}>;
export type PayrollPeriodEmployees = Readonly<{
  items: readonly PayrollPeriodEmployee[];
  nextCursor: string | null;
}>;
