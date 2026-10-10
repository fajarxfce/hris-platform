import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmployeeImportCreationController } from "../controllers/employee-import-creation-controller";
import { useEmployeeImportCreationForm } from "../controllers/use-employee-import-creation-form";
import { employeeImportCreationMessages } from "../i18n/employee-import-creation-messages";
import { employeeImportMessages } from "../i18n/employee-import-messages";
import { employeeImportParameters } from "../models/employee-import-route";
import { EmployeeImportCreationPage } from "../pages/employee-import-creation-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  people: PeopleUseCases;
  companyName: string;
  locale: Locale;
  nextIdentifier: () => string;
};
export function EmployeeImportCreationScreen(props: Props) {
  const [parameters] = useSearchParams();
  const query = employeeImportParameters(
    props.access.companyId,
    parameters.get("company") === props.access.companyId ? parameters.get("after") : null,
  ).toString();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/people/imports?${query}`} replace />;
  if (!parameters.has("company")) return <Navigate to={`/people/imports/new?${query}`} replace />;
  return (
    <EmployeeImportCreationBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      query={query}
    />
  );
}
function EmployeeImportCreationBinding(props: Props & { query: string }) {
  const controller = useMemo(
    () => new EmployeeImportCreationController(props.people, props.access, props.nextIdentifier),
    [props.people, props.access, props.nextIdentifier],
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
  const form = useEmployeeImportCreationForm(controller, state);
  useWorkspaceRevalidation(state.failure);
  const file = useMemo(
    () =>
      state.file
        ? [
            { label: employeeImportMessages(props.locale).fileName, value: state.file.name },
            {
              label: employeeImportCreationMessages(props.locale).size,
              value: `${new Intl.NumberFormat(props.locale).format(state.file.byteLength)} ${employeeImportCreationMessages(props.locale).bytes}`,
            },
          ]
        : null,
    [state.file, props.locale],
  );
  return (
    <EmployeeImportCreationPage
      state={state}
      form={form}
      file={file}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`/people/imports?${props.query}`}
      openTo={
        state.receipt
          ? `/people/imports/${encodeURIComponent(state.receipt.id)}?${props.query}`
          : null
      }
      onSelect={controller.selectFile}
      onClear={controller.clearFile}
      onDownload={controller.downloadTemplate}
      onRetry={controller.retry}
    />
  );
}
