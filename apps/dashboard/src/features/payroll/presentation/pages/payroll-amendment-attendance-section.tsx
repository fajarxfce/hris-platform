import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { payrollAmendmentAttendanceView } from "../models/payroll-amendment-attendance-view";

export function PayrollAmendmentAttendanceSection({
  view,
}: {
  view: ReturnType<typeof payrollAmendmentAttendanceView>;
}) {
  return (
    view && (
      <details className="app-amendment-disclosure">
        <summary>{view.title}</summary>
        <div className="app-amendment-evidence">
          <AppPropertyList title={view.title} items={view.properties} />
          <AppResourceTable
            title={view.title}
            columns={view.columns}
            rows={view.rows}
            minWidth={800}
          />
        </div>
      </details>
    )
  );
}
