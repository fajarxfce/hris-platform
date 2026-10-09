import { z } from "zod";
import { clientBuildsDto, clientModuleDto, maintenanceWindowDto } from "./client-policy-values-dto";

export const clientPolicyRevisionDto = z.object({
  version: z.number().int().min(0).max(9999),
  activateAt: z.iso.datetime(),
  disabledModules: z.array(clientModuleDto).max(8),
  minimumBuilds: clientBuildsDto,
  maintenance: maintenanceWindowDto.nullable(),
  recordedAt: z.iso.datetime(),
  actorId: z.uuid(),
  reason: z.string().min(1).max(1000),
});
export type ClientPolicyRevisionDto = z.infer<typeof clientPolicyRevisionDto>;
