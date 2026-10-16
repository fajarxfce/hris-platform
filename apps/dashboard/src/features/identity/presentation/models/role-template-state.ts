import type { Failure } from "../../../../core/domain/result";
import type { RoleTemplateReview } from "../../domain/entities/role-template-review";
import type { RoleTemplateRevision } from "../../domain/entities/role-template-revision";
export type RoleTemplateState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  review: RoleTemplateReview | null;
  selectedRevision: RoleTemplateRevision | null;
  failure: Failure | null;
}>;
export const initialRoleTemplateState: RoleTemplateState = Object.freeze({
  stage: "loading",
  review: null,
  selectedRevision: null,
  failure: null,
});
