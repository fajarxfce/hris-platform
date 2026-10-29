import { useContext, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollComparisonController } from "../controllers/payroll-comparison-controller";
import { payrollRunPath } from "../models/payroll-calculation-route";
import { PayrollComparisonPage } from "../pages/payroll-comparison-page";
export function PayrollComparisonScreen(props: PayrollScreenProps) {
  const { runId = "" } = useParams(),
    [params] = useSearchParams();
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/periods?company=${props.access.companyId}`} replace />;
  if (!params.has("company")) {
    const pinned = new URLSearchParams(params);
    pinned.set("company", props.access.companyId);
    return (
      <Navigate to={`/payroll/runs/${encodeURIComponent(runId)}/comparison?${pinned}`} replace />
    );
  }
  return (
    <ComparisonBinding
      key={`${props.accountId}:${props.access.companyId}:${runId}`}
      {...props}
      id={runId}
    />
  );
}
function ComparisonBinding(props: PayrollScreenProps & { id: string }) {
  const [params] = useSearchParams(),
    navigate = useNavigate(),
    visible = useContext(WorkspaceVisibilityContext);
  const [foreground, setForeground] = useState(() => document.visibilityState !== "hidden"),
    [month, setMonth] = useState(params.get("month") ?? "");
  const baseline = params.get("baselineId"),
    searchedMonth = params.get("month"),
    periodAfter = params.get("periodAfter"),
    after = params.get("after"),
    changedOnly = params.get("changedOnly") === "true";
  const controller = useMemo(
    () =>
      new PayrollComparisonController(
        props.payroll,
        props.access,
        props.id,
        baseline,
        searchedMonth,
        periodAfter,
        after,
        changedOnly,
      ),
    [
      props.payroll,
      props.access,
      props.id,
      baseline,
      searchedMonth,
      periodAfter,
      after,
      changedOnly,
    ],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    const changed = () => setForeground(document.visibilityState !== "hidden");
    changed();
    document.addEventListener("visibilitychange", changed);
    return () => document.removeEventListener("visibilitychange", changed);
  }, []);
  useEffect(() => {
    if (visible && foreground) controller.activate();
    else controller.deactivate();
    return controller.deactivate;
  }, [controller, visible, foreground]);
  useEffect(() => {
    if (state.context) setMonth(state.context.month);
  }, [state.context]);
  useWorkspaceRevalidation(state.failure);
  const go = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value === null) next.delete(key);
      else next.set(key, value);
    }
    navigate(`/payroll/runs/${encodeURIComponent(props.id)}/comparison?${next}`);
  };
  return (
    <PayrollComparisonPage
      state={state}
      companyId={props.access.companyId}
      companyName={props.companyName}
      locale={props.locale}
      backTo={payrollRunPath(props.access.companyId, props.id)}
      month={month}
      periodFirst={periodAfter === null}
      employeeFirst={after === null}
      changedOnly={changedOnly}
      baselineSelected={baseline !== null}
      onMonth={setMonth}
      onSearch={() => go({ month, periodAfter: null, baselineId: null, after: null })}
      onChoose={(id) => {
        const period = state.context?.periods.items.find((p) => p.id === id);
        if (period?.currentRunId) go({ baselineId: period.currentRunId, after: null });
      }}
      onPeriodFirst={() => go({ periodAfter: null })}
      onPeriodNext={() => {
        if (state.context?.periods.nextCursor)
          go({ periodAfter: state.context.periods.nextCursor });
      }}
      onEmployeeFirst={() => go({ after: null })}
      onEmployeeNext={() => {
        if (state.comparison?.employees.nextCursor)
          go({ after: state.comparison.employees.nextCursor });
      }}
      onFilter={(value) => go({ changedOnly: String(value), after: null })}
      onRefresh={controller.refresh}
      onSelect={controller.select}
      onDismiss={controller.dismiss}
    />
  );
}
