import { z } from "zod";
import type {
  clientBuildsDto,
  clientModuleDto,
  MaintenanceWindowDto,
} from "./client-policy-values-dto";

export type ClientPolicyChangeDto = Readonly<{
  expectedVersion: number | null;
  activateAt: string | null;
  disabledModules: readonly z.infer<typeof clientModuleDto>[];
  minimumBuilds: z.infer<typeof clientBuildsDto>;
  maintenance: MaintenanceWindowDto | null;
  reason: string;
}>;
export const clientPolicyReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(9999),
});
export type ClientPolicyReceiptDto = z.infer<typeof clientPolicyReceiptDto>;
