import type { Failure } from "../../../../core/domain/result";
import type { IntegrationConfiguration } from "../../domain/entities/integration-configuration";

export type IntegrationConfigurationState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  review: IntegrationConfiguration | null;
  failure: Failure | null;
}>;
export const initialIntegrationConfigurationState: IntegrationConfigurationState = Object.freeze({
  stage: "idle",
  review: null,
  failure: null,
});
