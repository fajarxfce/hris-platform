import type { Failure } from "../../../../core/domain/result";
import type { RoleTemplatePage } from "../../domain/entities/role-template";
export type RoleTemplatesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: RoleTemplatePage | null;
  failure: Failure | null;
}>;
export const initialRoleTemplatesState: RoleTemplatesState = Object.freeze({
  stage: "loading",
  page: null,
  failure: null,
});
