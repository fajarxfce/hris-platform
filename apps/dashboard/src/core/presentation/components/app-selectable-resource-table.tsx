import {
  Table,
  TableBody,
  TableCell,
  TableHeader,
  TableHeaderCell,
  TableRow,
} from "@fluentui/react-components";
import { AppCheckbox } from "./app-checkbox";
export function AppSelectableResourceTable(props: {
  title: string;
  columns: readonly Readonly<{ id: string; label: string }>[];
  rows: readonly Readonly<{
    id: string;
    label: string;
    cells: readonly string[];
    selected: boolean;
  }>[];
  selectLabel: string;
  selectPageLabel: string;
  pageChecked: boolean | "mixed";
  onToggle: (id: string, selected: boolean) => void;
  onTogglePage: (selected: boolean) => void;
}) {
  return (
    <section className="app-resource-panel" aria-label={props.title}>
      <h2>{props.title}</h2>
      <div className="app-table-scroll app-selection-scroll">
        <Table aria-label={props.title} style={{ minWidth: 480 }}>
          <TableHeader>
            <TableRow>
              <TableHeaderCell style={{ width: 48 }}>
                <AppCheckbox
                  aria-label={props.selectPageLabel}
                  checked={props.pageChecked}
                  onChange={(_, data) => {
                    if (typeof data.checked === "boolean") props.onTogglePage(data.checked);
                  }}
                />
              </TableHeaderCell>
              {props.columns.map((column) => (
                <TableHeaderCell key={column.id}>{column.label}</TableHeaderCell>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {props.rows.map((row) => (
              <TableRow key={row.id}>
                <TableCell>
                  <AppCheckbox
                    aria-label={`${props.selectLabel}: ${row.label}`}
                    checked={row.selected}
                    onChange={(_, data) => {
                      if (typeof data.checked === "boolean") props.onToggle(row.id, data.checked);
                    }}
                  />
                </TableCell>
                {props.columns.map((column, index) => (
                  <TableCell key={column.id}>{row.cells[index]}</TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </section>
  );
}
