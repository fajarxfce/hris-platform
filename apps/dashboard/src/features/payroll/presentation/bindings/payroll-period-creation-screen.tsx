import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollPeriodCreationController } from "../controllers/payroll-period-creation-controller";
import { usePayrollEmployeeSearch } from "../controllers/use-payroll-employee-search";
import { usePayrollPeriodCreationForm } from "../controllers/use-payroll-period-creation-form";
import { payrollPeriodCreationView } from "../models/payroll-period-view";
import { PayrollPeriodCreationPage } from "../pages/payroll-period-creation-page";
export function PayrollPeriodCreationScreen(props: PayrollScreenProps) {
  const [parameters] = useSearchParams();
  const currentMonth = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 7),
    [props.timezone],
  );
  const month = parameters.get("month") ?? currentMonth;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/periods?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company") || !parameters.has("month"))
    return (
      <Navigate
        to={`/payroll/periods/new?${new URLSearchParams({ company: props.access.companyId, month })}`}
        replace
      />
    );
  return (
    <PayrollPeriodCreationBinding
      key={`${props.accountId}:${props.access.companyId}:${month}`}
      {...props}
      month={month}
    />
  );
}
function PayrollPeriodCreationBinding(props: PayrollScreenProps & { month: string }) {
  const { payroll, access, nextIdentifier } = props;
  const controller = useMemo(
    () => new PayrollPeriodCreationController(payroll, access, nextIdentifier),
    [payroll, access, nextIdentifier],
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
  useWorkspaceRevalidation(state.failure ?? state.searchFailure);
  const form = usePayrollPeriodCreationForm(controller, state, props.month),
    search = usePayrollEmployeeSearch(state.query, (query) => {
      void controller.search(query, null);
    });
  const view = useMemo(() => payrollPeriodCreationView(state, props.locale), [state, props.locale]);
  return (
    <PayrollPeriodCreationPage
      state={state}
      view={view}
      form={form}
      search={search}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`/payroll/periods?company=${access.companyId}`}
      savedTo={
        state.receipt ? `/payroll/periods/${state.receipt.id}?company=${access.companyId}` : null
      }
      onToggle={controller.toggle}
      onTogglePage={controller.togglePage}
      onRemove={controller.remove}
      onSelectedPage={controller.selectPage}
      onRefresh={() => {
        void controller.search(state.query, state.after);
      }}
      onFirst={() => {
        void controller.search(state.query, null);
      }}
      onNext={() => {
        if (state.page?.nextCursor) void controller.search(state.query, state.page.nextCursor);
      }}
      onEdit={controller.edit}
      onConfirm={controller.confirm}
      onRetry={controller.retry}
    />
  );
}
