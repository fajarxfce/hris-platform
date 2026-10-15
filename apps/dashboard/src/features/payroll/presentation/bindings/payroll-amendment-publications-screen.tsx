import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canPublishPayrollAmendments } from "../../domain/policies/payroll-amendment-publication-policy";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollAmendmentReader } from "../controllers/payroll-amendment-reader";
import {
  payrollAmendmentPublicationPath,
  payrollAmendmentPublicationStartPath,
  payrollAmendmentPublicationsPath,
} from "../models/payroll-amendment-publication-route";
import {
  payrollAmendmentPublicationRows,
  payrollAmendmentPublicationSummary,
} from "../models/payroll-amendment-publication-view";
import { payrollAmendmentPath } from "../models/payroll-amendment-route";
import { PayrollAmendmentPublicationsPage } from "../pages/payroll-amendment-publications-page";

export function PayrollAmendmentPublicationsScreen(props: PayrollScreenProps) {
  const { amendmentId = "" } = useParams(),
    [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/amendments?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={payrollAmendmentPublicationsPath(props.access.companyId, amendmentId, parameters)}
        replace
      />
    );
  return (
    <PayrollAmendmentPublicationsBinding
      key={`${props.accountId}:${props.access.companyId}:${amendmentId}`}
      {...props}
      id={amendmentId}
    />
  );
}
function PayrollAmendmentPublicationsBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, locale } = props,
    [parameters] = useSearchParams(),
    navigate = useNavigate(),
    controller = useMemo(
      () => new PayrollAmendmentReader(payroll.loadAmendmentPublications, access, id),
      [payroll.loadAmendmentPublications, access, id],
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
  const properties = useMemo(
      () => payrollAmendmentPublicationSummary(state.data?.amendment ?? null, locale),
      [state.data, locale],
    ),
    rows = useMemo(
      () => payrollAmendmentPublicationRows(state.data?.attempts ?? [], locale),
      [state.data, locale],
    );
  const back = new URLSearchParams(parameters);
  for (const key of ["revision", "historyAfter", "assessment", "tab"]) back.delete(key);
  return (
    <PayrollAmendmentPublicationsPage
      state={state}
      properties={properties}
      rows={rows}
      locale={locale}
      companyName={props.companyName}
      backTo={state.data ? payrollAmendmentPath(access.companyId, id, back) : null}
      startTo={
        canPublishPayrollAmendments(access.permissions) &&
        state.data?.availableActions.includes("START")
          ? payrollAmendmentPublicationStartPath(access.companyId, id, parameters)
          : null
      }
      onRefresh={controller.refresh}
      onPublication={(publication) => {
        if (state.data?.attempts.some((value) => value.publication.id === publication))
          navigate(payrollAmendmentPublicationPath(access.companyId, id, publication, back));
      }}
    />
  );
}
