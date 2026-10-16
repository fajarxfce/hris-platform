import type { CompanyId } from "../../../../core/domain/identifiers";

export type RoleTemplateId = string & { readonly roleTemplateId: unique symbol };
export type RoleTemplate = Readonly<{
  id: RoleTemplateId;
  companyId: CompanyId;
  code: string;
  name: string;
  permissions: readonly string[];
  active: boolean;
  version: number;
}>;
export type RoleTemplatePage = Readonly<{
  items: readonly RoleTemplate[];
  nextCursor: string | null;
}>;
