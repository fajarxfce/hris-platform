import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollAmendmentEditorTarget } from "../../domain/entities/payroll-amendment-preparation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollAmendmentEditorController } from "../controllers/payroll-amendment-editor-controller";
import { usePayrollAmendmentEditorForm } from "../controllers/use-payroll-amendment-editor-form";
import { payrollAmendmentEditorView } from "../models/payroll-amendment-editor-view";
import { payrollAmendmentPath } from "../models/payroll-amendment-route";
import { PayrollAmendmentEditorPage } from "../pages/payroll-amendment-editor-page";
import { PayrollAmendmentCorrectionBinding } from "./payroll-amendment-correction-binding";
export function PayrollAmendmentEditorScreen(
  props: PayrollScreenProps & { mode: "create" | "edit" },
) {
  const { amendmentId = "", assessmentId = "" } = useParams(),
    [parameters] = useSearchParams(),
    id = props.mode === "create" ? assessmentId : amendmentId;
  const target = useMemo<PayrollAmendmentEditorTarget>(
    () =>
      props.mode === "create"
        ? { kind: "create", assessmentId: id }
        : { kind: "edit", amendmentId: id },
    [props.mode, id],
  );
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/amendments?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company")) {
    const next = new URLSearchParams(parameters);
    next.set("company", props.access.companyId);
    return (
      <Navigate
        to={`/payroll/amendments/${props.mode === "create" ? `new/${encodeURIComponent(id)}` : `${encodeURIComponent(id)}/edit`}?${next}`}
        replace
      />
    );
  }
  return (
    <PayrollAmendmentEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${props.mode}:${id}`}
      {...props}
      target={target}
    />
  );
}
function PayrollAmendmentEditorBinding(
  props: PayrollScreenProps & { target: PayrollAmendmentEditorTarget },
) {
  const { payroll, access, target, nextIdentifier, locale } = props,
    [parameters] = useSearchParams();
  const controller = useMemo(
    () => new PayrollAmendmentEditorController(payroll, access, target, nextIdentifier),
    [payroll, access, target, nextIdentifier],
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
  const form = usePayrollAmendmentEditorForm(controller, state),
    view = useMemo(() => payrollAmendmentEditorView(state, locale), [state, locale]);
  const back = new URLSearchParams(parameters),
    saved = new URLSearchParams(parameters);
  for (const key of ["revision", "historyAfter", "tab", "assessment"]) back.delete(key);
  if (state.receipt) saved.set("revision", String(state.receipt.version));
  saved.delete("historyAfter");
  saved.delete("assessment");
  saved.set("tab", "proposal");
  const selected = state.context?.preparation.assessments.find(
      (row) => row.source.assessmentId === state.selectedAssessment,
    ),
    correction =
      state.terms?.corrections.find((row) => row.assessmentId === state.selectedAssessment) ?? null;
  return (
    <PayrollAmendmentEditorPage
      state={state}
      view={view}
      locale={locale}
      companyName={props.companyName}
      form={form}
      backTo={
        target.kind === "create"
          ? `/payroll/amendments/new?${back}`
          : payrollAmendmentPath(access.companyId, target.amendmentId, back)
      }
      savedTo={
        state.receipt ? payrollAmendmentPath(access.companyId, state.receipt.id, saved) : null
      }
      onMonth={controller.openCorrection}
      onPreview={controller.preview}
      onAcceptSources={controller.acceptCurrentSources}
      onRetry={controller.retrySave}
    >
      {selected && (
        <PayrollAmendmentCorrectionBinding
          key={selected.source.assessmentId}
          source={selected}
          correction={correction}
          locale={locale}
          timezone={props.timezone}
          failure={state.failure}
          correctionIndex={
            state.terms?.corrections.findIndex(
              (row) => row.assessmentId === selected.source.assessmentId,
            ) ?? -1
          }
          onApply={controller.applyCorrection}
          onClose={controller.closeCorrection}
          onRemove={controller.removeCorrection}
          onDirtyChange={form.changeMonthDirty}
        />
      )}
    </PayrollAmendmentEditorPage>
  );
}
