import { z } from "zod";
import { integrationKinds } from "../../domain/entities/integration-configuration";

export const integrationConfigurationDto = z.strictObject({
  runtime: z.literal("API"),
  observedAt: z.iso.datetime(),
  integrations: z
    .array(z.strictObject({ kind: z.enum(integrationKinds), enabled: z.boolean() }))
    .length(integrationKinds.length)
    .refine((items) => new Set(items.map((item) => item.kind)).size === integrationKinds.length),
});
