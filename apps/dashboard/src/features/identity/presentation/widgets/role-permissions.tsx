import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import type { rolePermissionGroups } from "../models/role-template-view";

export function RolePermissions({
  title,
  empty,
  groups,
  editable,
  onSelect,
}: {
  title: string;
  empty: string;
  groups: ReturnType<typeof rolePermissionGroups>;
  editable?: boolean;
  onSelect?: (permission: string, selected: boolean) => void;
}) {
  return (
    <section className="app-resource-panel app-role-permissions" aria-label={title}>
      <h2>{title}</h2>
      {groups.length === 0 && <p className="app-role-permission-empty">{empty}</p>}
      {groups.map((group) => (
        <details key={group.id} className="app-role-permission-group">
          <summary>
            <span>{group.label}</span>
            {onSelect && <span className="app-role-permission-count">{group.selected}</span>}
          </summary>
          {onSelect ? (
            <div className="app-role-permission-options">
              {group.items.map((permission) => (
                <AppCheckbox
                  key={permission.id}
                  label={permission.label}
                  title={permission.id}
                  checked={permission.checked}
                  disabled={!editable}
                  onChange={(_, data) => onSelect(permission.id, data.checked === true)}
                />
              ))}
            </div>
          ) : (
            <ul>
              {group.items.map((permission) => (
                <li key={permission.id} title={permission.id}>
                  {permission.label}
                </li>
              ))}
            </ul>
          )}
        </details>
      ))}
    </section>
  );
}
