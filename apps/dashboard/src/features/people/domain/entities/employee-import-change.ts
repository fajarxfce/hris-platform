export type EmployeeImportAction = "apply" | "resume" | "cancel";
export type EmployeeImportChange = Readonly<{
  importId: string;
  expectedVersion: number;
  reason: string;
}>;
export type EmployeeImportApplication = EmployeeImportChange & Readonly<{ allowPartial: boolean }>;
