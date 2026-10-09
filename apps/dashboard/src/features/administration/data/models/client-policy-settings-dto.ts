import { z } from "zod";
import { clientPolicyRevisionDto } from "./client-policy-revision-dto";
import { clientBuildsDto, clientModuleDto, maintenanceWindowDto } from "./client-policy-values-dto";

export const clientPolicySettingsDto = z.object({
  latest: clientPolicyRevisionDto.nullable(),
  effective: z.object({
    schemaVersion: z.literal(1),
    version: z.number().int().min(0).max(9999).nullable(),
    enabledModules: z.array(clientModuleDto).max(8),
    minimumBuilds: clientBuildsDto,
    maintenance: maintenanceWindowDto.nullable(),
    maintenanceActive: z.boolean(),
    serverTime: z.iso.datetime(),
    validUntil: z.iso.datetime(),
  }),
});
export type ClientPolicySettingsDto = z.infer<typeof clientPolicySettingsDto>;
