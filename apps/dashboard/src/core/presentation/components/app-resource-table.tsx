import {
  Table,
  TableBody,
  TableCell,
  TableHeader,
  TableHeaderCell,
  TableRow,
} from "@fluentui/react-components";

export function AppResourceTable({
  title,
  columns,
  rows,
}: {
  title: string;
  columns: readonly Readonly<{ id: string; label: string; numeric?: boolean }>[];
  rows: readonly Readonly<{ id: string; cells: readonly string[] }>[];
}) {
  return (
    <section className="app-resource-panel" aria-label={title}>
      <h2>{title}</h2>
      <div className="app-table-scroll">
        <Table aria-label={title}>
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
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </section>
  );
}
