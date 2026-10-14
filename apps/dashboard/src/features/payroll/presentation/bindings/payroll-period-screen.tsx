import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canPreparePayrollPeriod } from "../../domain/policies/payroll-period-policy";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollPeriodController } from "../controllers/payroll-period-controller";
import { payrollPeriodDetailsParameters } from "../models/payroll-period-route";
import { payrollPeriodDetailsView } from "../models/payroll-period-view";
import { PayrollPeriodPage } from "../pages/payroll-period-page";
export function PayrollPeriodScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams(),
    { periodId = "" } = useParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/periods?company=${props.access.companyId}`} replace />;
  const memberAfter = parameters.get("memberAfter"),
    historyAfter = parameters.get("historyAfter"),
    tab = parameters.get("tab") === "history" ? "history" : "participants";
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`/payroll/periods/${encodeURIComponent(periodId)}?${payrollPeriodDetailsParameters(props.access.companyId, memberAfter, historyAfter, tab)}`}
        replace
      />
    );
  return (
    <PayrollPeriodBinding
      key={`${props.accountId}:${props.access.companyId}:${periodId}`}
      {...props}
      id={periodId}
      memberAfter={memberAfter}
      historyAfter={historyAfter}
      tab={tab}
      onSelection={(memberAfter, historyAfter, tab) =>
        setParameters(
          payrollPeriodDetailsParameters(props.access.companyId, memberAfter, historyAfter, tab),
        )
      }
    />
  );
}
function PayrollPeriodBinding(
  props: PayrollScreenProps & {
    id: string;
    memberAfter: string | null;
    historyAfter: string | null;
    tab: string;
    onSelection: (memberAfter: string | null, historyAfter: string | null, tab: string) => void;
  },
) {
  const { payroll, access, id, memberAfter, historyAfter, tab } = props,
    navigate = useNavigate();
  const controller = useMemo(
    () => new PayrollPeriodController(payroll.loadPeriod, access, id, memberAfter, historyAfter),
    [payroll.loadPeriod, access, id, memberAfter, historyAfter],
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
    () => (state.details ? payrollPeriodDetailsView(state.details, props.locale) : null),
    [state.details, props.locale],
  );
  const page = tab === "history" ? state.details?.history : state.details?.members;
  return (
    <PayrollPeriodPage
      state={state}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`/payroll/periods?company=${access.companyId}`}
      tab={tab}
      canCancel={
        canPreparePayrollPeriod(access.permissions) &&
        state.details?.period.status === "DRAFT" &&
        state.details.period.version < 255
      }
      onCancel={() =>
        navigate(`/payroll/periods/${encodeURIComponent(id)}/cancel?company=${access.companyId}`)
      }
      onRefresh={controller.refresh}
      onTab={(tab) => {
        if (tab === "participants" || tab === "history")
          props.onSelection(memberAfter, historyAfter, tab);
      }}
      firstPage={(tab === "history" ? historyAfter : memberAfter) === null}
      hasNext={page?.nextCursor != null}
      onFirst={() =>
        props.onSelection(
          tab === "history" ? memberAfter : null,
          tab === "history" ? null : historyAfter,
          tab,
        )
      }
      onNext={() => {
        if (page?.nextCursor)
          props.onSelection(
            tab === "history" ? memberAfter : page.nextCursor,
            tab === "history" ? page.nextCursor : historyAfter,
            tab,
          );
      }}
    />
  );
}
