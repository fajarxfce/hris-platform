export type RoleTemplateChangeDto = {
  code: string;
  name: string;
  permissions: string[];
  active: boolean;
  expectedVersion: number | null;
  reason: string;
};
