import type { RoleTemplate } from "../../domain/entities/role-template";
export type RoleTemplateFormValues = {
  code: string;
  name: string;
  active: boolean;
  permissions: string[];
  reason: string;
};
export function roleTemplateFormValues(template: RoleTemplate | null): RoleTemplateFormValues {
  return {
    code: template?.code ?? "",
    name: template?.name ?? "",
    active: template?.active ?? true,
    permissions: [...(template?.permissions ?? [])],
    reason: "",
  };
}
