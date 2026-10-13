import {
  Table,
  TableBody,
  TableCell,
  TableHeader,
  TableHeaderCell,
  TableRow,
  useRestoreFocusTarget,
} from "@fluentui/react-components";
import { AppButton } from "./app-button";

export function AppResourceTable({
  title,
  columns,
  rows,
  action,
  minWidth,
}: {
  title: string;
  columns: readonly Readonly<{ id: string; label: string; numeric?: boolean }>[];
  rows: readonly Readonly<{
    id: string;
    actionLabel?: string;
    actionDisabled?: boolean;
    cells: readonly string[];
  }>[];
  action?: Readonly<{ label: string; onOpen: (id: string) => void; disabled?: boolean }>;
  minWidth?: number;
}) {
  const restoreFocus = useRestoreFocusTarget();
  return (
    <section className="app-resource-panel" aria-label={title}>
      <h2>{title}</h2>
      <div className="app-table-scroll">
        <Table
          aria-label={title}
          style={{ minWidth: minWidth ?? columns.length * 160 + (action ? 120 : 0) }}
        >
          <TableHeader>
            <TableRow>
              {columns.map((column) => (
                <TableHeaderCell
                  key={column.id}
                  className={column.numeric ? "app-table-number" : undefined}
                  button={{ style: { justifyContent: column.numeric ? "flex-end" : "flex-start" } }}
                >
                  {column.label}
                </TableHeaderCell>
              ))}
              {action && <TableHeaderCell>{action.label}</TableHeaderCell>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id}>
                {columns.map((column, index) => (
                  <TableCell
                    key={column.id}
                    className={column.numeric ? "app-table-number" : undefined}
                  >
                    {row.cells[index]}
                  </TableCell>
                ))}
                {action && (
                  <TableCell className="app-table-action">
                    <AppButton
                      {...restoreFocus}
                      disabled={action.disabled === true || row.actionDisabled === true}
                      appearance="subtle"
                      aria-label={`${action.label}: ${row.actionLabel ?? row.id}`}
                      onClick={() => action.onOpen(row.id)}
                    >
                      {action.label}
                    </AppButton>
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </section>
  );
}
