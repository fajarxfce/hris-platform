import type { Failure } from "../../../../core/domain/result";
import type { LifecycleTemplate } from "../../domain/entities/lifecycle-template";

export type LifecycleTemplateState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  template: LifecycleTemplate | null;
  failure: Failure | null;
}>;
export const initialLifecycleTemplateState: LifecycleTemplateState = Object.freeze({
  stage: "idle",
  template: null,
  failure: null,
});
