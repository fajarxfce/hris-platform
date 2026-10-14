import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollReviewController } from "../controllers/payroll-review-controller";
import { payrollRunPath } from "../models/payroll-calculation-route";
import {
  payrollReviewActionPath,
  payrollReviewPath,
  payrollReviewsPath,
} from "../models/payroll-review-route";
import { payrollReviewView } from "../models/payroll-review-view";
import { PayrollReviewPage } from "../pages/payroll-review-page";
export function PayrollReviewScreen(props: PayrollScreenProps) {
  const { reviewId = "" } = useParams(),
    [params, setParams] = useSearchParams(),
    tab =
      params.get("tab") === "stages"
        ? "stages"
        : params.get("tab") === "history"
          ? "history"
          : "overview";
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/periods?company=${props.access.companyId}`} replace />;
  if (!params.has("company"))
    return (
      <Navigate to={`${payrollReviewPath(props.access.companyId, reviewId)}&tab=${tab}`} replace />
    );
  return (
    <PayrollReviewBinding
      key={`${props.accountId}:${props.access.companyId}:${reviewId}`}
      {...props}
      id={reviewId}
      tab={tab}
      onTab={(value) => {
        if (["overview", "stages", "history"].includes(value))
          setParams({ company: props.access.companyId, tab: value });
      }}
    />
  );
}
function PayrollReviewBinding(
  props: PayrollScreenProps & { id: string; tab: string; onTab: (tab: string) => void },
) {
  const { payroll, access, id, locale } = props,
    controller = useMemo(
      () => new PayrollReviewController(payroll.loadReview, access, id),
      [payroll.loadReview, access, id],
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
    () => (state.data ? payrollReviewView(state.data, locale) : null),
    [state.data, locale],
  );
  return (
    <PayrollReviewPage
      state={state}
      view={view}
      locale={locale}
      companyName={props.companyName}
      backTo={state.data ? payrollReviewsPath(access.companyId, state.data.run.id) : null}
      runTo={state.data ? payrollRunPath(access.companyId, state.data.run.id) : null}
      actionLinks={
        state.data?.availableActions.map((action) => ({
          action,
          to: payrollReviewActionPath(access.companyId, id, action),
        })) ?? []
      }
      tab={props.tab}
      onRefresh={controller.refresh}
      onTab={props.onTab}
    />
  );
}
