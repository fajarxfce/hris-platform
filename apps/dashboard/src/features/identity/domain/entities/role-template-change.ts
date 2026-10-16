export type RoleTemplateChange = Readonly<{
  id: string;
  expectedVersion: number | null;
  code: string;
  name: string;
  permissions: readonly string[];
  active: boolean;
  reason: string;
}>;
