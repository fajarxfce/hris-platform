import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollRunEmployeeController } from "../controllers/payroll-run-employee-controller";
import { payrollRunEmployeePath, payrollRunPath } from "../models/payroll-calculation-route";
import { payrollRunEmployeeView } from "../models/payroll-run-employee-view";
import { PayrollRunEmployeePage } from "../pages/payroll-run-employee-page";
export function PayrollRunEmployeeScreen(props: PayrollScreenProps) {
  const { runId = "", employeeId = "" } = useParams(),
    [params, setParams] = useSearchParams();
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/periods?company=${props.access.companyId}`} replace />;
  const after = params.get("after"),
    tab = params.get("tab") === "sources" ? "sources" : "calculation";
  if (!params.has("company"))
    return (
      <Navigate
        to={payrollRunEmployeePath(props.access.companyId, runId, employeeId, after)}
        replace
      />
    );
  return (
    <PayrollRunEmployeeBinding
      key={`${props.accountId}:${props.access.companyId}:${runId}:${employeeId}`}
      {...props}
      id={runId}
      employeeId={employeeId}
      after={after}
      tab={tab}
      onTab={(selected) => {
        if (["calculation", "sources"].includes(selected)) {
          const next = new URLSearchParams(params);
          next.set("tab", selected);
          setParams(next);
        }
      }}
    />
  );
}
function PayrollRunEmployeeBinding(
  props: PayrollScreenProps & {
    id: string;
    employeeId: string;
    after: string | null;
    tab: string;
    onTab: (tab: string) => void;
  },
) {
  const { payroll, access, id, employeeId, locale } = props,
    controller = useMemo(
      () => new PayrollRunEmployeeController(payroll.loadRunEmployee, access, id, employeeId),
      [payroll.loadRunEmployee, access, id, employeeId],
    );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
    () => (state.employee ? payrollRunEmployeeView(state.employee, locale) : null),
    [state.employee, locale],
  );
  return (
    <PayrollRunEmployeePage
      state={state}
      view={view}
      locale={locale}
      companyName={props.companyName}
      backTo={payrollRunPath(access.companyId, id, props.after)}
      tab={props.tab}
      onTab={props.onTab}
      onRefresh={controller.refresh}
    />
  );
}
