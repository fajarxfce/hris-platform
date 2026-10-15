import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollAmendmentController } from "../controllers/payroll-amendment-controller";
import {
  payrollAmendmentPath,
  payrollAmendmentSelectionFromRoute,
} from "../models/payroll-amendment-route";
import { payrollAmendmentInspectionView } from "../models/payroll-amendment-view";
import { PayrollAmendmentPage } from "../pages/payroll-amendment-page";
export function PayrollAmendmentScreen(props: PayrollScreenProps) {
  const { amendmentId = "" } = useParams(),
    [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/amendments?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={payrollAmendmentPath(props.access.companyId, amendmentId, parameters)}
        replace
      />
    );
  return (
    <PayrollAmendmentBinding
      key={`${props.accountId}:${props.access.companyId}:${amendmentId}`}
      {...props}
      id={amendmentId}
    />
  );
}
function PayrollAmendmentBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, locale, timezone } = props,
    [parameters, setParameters] = useSearchParams(),
    { revision, after } = payrollAmendmentSelectionFromRoute(parameters),
    controller = useMemo(
      () => new PayrollAmendmentController(payroll.loadAmendment, access, id, { revision, after }),
      [payroll.loadAmendment, access, id, revision, after],
    ),
    tab =
      ["overview", "proposal", "calculation", "history"].find(
        (value) => value === parameters.get("tab"),
      ) ?? "overview";
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
  const selectedAssessment = parameters.get("assessment"),
    view = useMemo(
      () =>
        state.data
          ? payrollAmendmentInspectionView(state.data, locale, timezone, selectedAssessment)
          : null,
      [state.data, locale, timezone, selectedAssessment],
    );
  const back = new URLSearchParams(parameters),
    latest = new URLSearchParams(parameters),
    basis = new URLSearchParams(parameters);
  for (const key of ["revision", "historyAfter", "assessment", "tab"]) back.delete(key);
  latest.delete("revision");
  latest.delete("assessment");
  if (state.data?.calculation) {
    basis.set("revision", String(state.data.calculation.summary.draftVersion));
    basis.set("tab", "proposal");
    basis.delete("assessment");
  }
  return (
    <PayrollAmendmentPage
      state={state}
      view={view}
      locale={locale}
      companyName={props.companyName}
      tab={tab}
      backTo={`/payroll/amendments?${back}`}
      latestTo={revision === null ? null : payrollAmendmentPath(access.companyId, id, latest)}
      basisTo={state.data?.calculation ? payrollAmendmentPath(access.companyId, id, basis) : null}
      first={after === null}
      onRefresh={controller.refresh}
      onTab={(nextTab) => {
        if (!["overview", "proposal", "calculation", "history"].includes(nextTab)) return;
        const next = new URLSearchParams(parameters);
        next.set("tab", nextTab);
        setParameters(next, { replace: true });
      }}
      onRevision={(value) => {
        if (!state.data?.history.items.some((row) => String(row.version) === value)) return;
        const next = new URLSearchParams(parameters);
        next.set("revision", value);
        next.set("tab", "proposal");
        next.delete("assessment");
        setParameters(next);
      }}
      onMonth={(value) => {
        if (
          !state.data?.calculation?.preview?.calculations.some(
            (row) => row.source.assessmentId === value,
          )
        )
          return;
        const next = new URLSearchParams(parameters);
        next.set("assessment", value);
        setParameters(next, { replace: true });
      }}
      onFirst={() => {
        const next = new URLSearchParams(parameters);
        next.delete("historyAfter");
        setParameters(next);
      }}
      onNext={() => {
        if (!state.data?.history.nextCursor) return;
        const next = new URLSearchParams(parameters);
        next.set("historyAfter", state.data.history.nextCursor);
        setParameters(next);
      }}
    />
  );
}
