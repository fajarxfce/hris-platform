import { z } from "zod";

export type EmployeeCreationDto = {
  id: string;
  employeeNumber: string;
  person: {
    id: string;
    legalName: string;
    birthDate: string | null;
    nationality: string;
    email: string | null;
  };
  terms: {
    effectiveFrom: string;
    startDate: string;
    endDate: string | null;
    contract: "PERMANENT" | "FIXED_TERM";
    status: "ACTIVE" | "PROBATION" | "SUSPENDED" | "ENDED";
    branchId: string | null;
    departmentId: string | null;
    positionId: string | null;
    costCenterId: string | null;
    managerId: string | null;
  };
  reason: string;
};
export const employeeCreationReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type EmployeeCreationReceiptDto = z.infer<typeof employeeCreationReceiptDto>;
