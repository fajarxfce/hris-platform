import type { Failure } from "../../../../core/domain/result";
import type { LifecycleTemplatePage } from "../../domain/entities/lifecycle-template-page";

export type LifecycleTemplatesState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  page: LifecycleTemplatePage | null;
  failure: Failure | null;
}>;
export const initialLifecycleTemplatesState: LifecycleTemplatesState = Object.freeze({
  stage: "idle",
  page: null,
  failure: null,
});
