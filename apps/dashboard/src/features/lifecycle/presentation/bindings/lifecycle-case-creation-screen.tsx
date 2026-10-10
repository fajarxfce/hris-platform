import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LoadEmployee } from "../../../people/domain/usecases/load-employee";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleCaseCreationController } from "../controllers/lifecycle-case-creation-controller";
import { useLifecycleCaseCreationForm } from "../controllers/use-lifecycle-case-creation-form";
import { lifecycleCaseTemplatePreview } from "../models/lifecycle-case-template-preview";
import { LifecycleCaseCreationPage } from "../pages/lifecycle-case-creation-page";
import { LifecycleTemplatePicker } from "./lifecycle-template-picker";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadEmployee: Pick<LoadEmployee, "execute">;
  lifecycle: LifecycleUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
};
export function LifecycleCaseCreationScreen(props: Props) {
  const { employeeId = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const asOf = parameters.get("asOf") ?? today;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`/people/employees?${new URLSearchParams({ company: props.access.companyId, asOf: today })}`}
        replace
      />
    );
  const scope = new URLSearchParams({ company: props.access.companyId, asOf });
  if (!parameters.has("company") || !parameters.has("asOf"))
    return (
      <Navigate
        to={`/people/employees/${encodeURIComponent(employeeId)}/lifecycle/new?${scope}`}
        replace
      />
    );
  return (
    <LifecycleCaseCreationBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${asOf}`}
      {...props}
      employeeId={employeeId}
      asOf={asOf}
      backTo={`/people/employees/${encodeURIComponent(employeeId)}?${scope}`}
    />
  );
}
function LifecycleCaseCreationBinding(
  props: Props & { employeeId: string; asOf: string; backTo: string },
) {
  const controller = useMemo(
    () =>
      new LifecycleCaseCreationController(
        props.loadEmployee,
        props.lifecycle.startCase,
        props.access,
        props.employeeId,
        props.asOf,
        props.nextIdentifier,
      ),
    [
      props.loadEmployee,
      props.lifecycle.startCase,
      props.access,
      props.employeeId,
      props.asOf,
      props.nextIdentifier,
    ],
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
  const form = useLifecycleCaseCreationForm(controller, state, props.asOf);
  const preview = useMemo(
    () =>
      form.template
        ? lifecycleCaseTemplatePreview(form.template, form.targetDate.value, props.locale)
        : [],
    [form.template, form.targetDate.value, props.locale],
  );
  useWorkspaceRevalidation(state.failure);
  const detailTo = state.receipt
    ? `/people/lifecycle/cases/${encodeURIComponent(state.receipt.id)}?${new URLSearchParams({ company: props.access.companyId, status: "OPEN" })}`
    : null;
  return (
    <>
      <LifecycleCaseCreationPage
        state={state}
        form={form}
        preview={preview}
        asOf={props.asOf}
        companyName={props.companyName}
        locale={props.locale}
        backTo={props.backTo}
        detailTo={detailTo}
        onRefresh={controller.refresh}
        onRetry={controller.retry}
      />
      {form.picker && form.editable && (
        <LifecycleTemplatePicker
          accountId={props.accountId}
          access={props.access}
          load={props.lifecycle.loadTemplates}
          locale={props.locale}
          onClose={form.closePicker}
          onSelect={form.select}
        />
      )}
    </>
  );
}
