import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { Failure } from "../../../../core/domain/result";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { ExpenseDraftReferences } from "../../domain/entities/expense-draft-references";
import type { ExpenseLine } from "../../domain/entities/expense-line";
import type { ExpenseScreenProps } from "../contracts/expense-screen-props";
import { ExpenseDraftEditorController } from "../controllers/expense-draft-editor-controller";
import { useExpenseDraftEmployees } from "../controllers/use-expense-draft-employees";
import { useExpenseDraftForm } from "../controllers/use-expense-draft-form";
import { useExpenseDraftOptions } from "../controllers/use-expense-draft-options";
import { useExpenseLineForm } from "../controllers/use-expense-line-form";
import { expenseDraftMessages } from "../i18n/expense-draft-messages";
import {
  expenseClaimParameters,
  expenseClaimPath,
  expenseClaimSearchFromRoute,
} from "../models/expense-claim-route";
import type { ExpenseDraftOption } from "../models/expense-draft-option";
import {
  expenseDraftEditorView,
  expenseDraftOptionRows,
  expenseLineSelectionView,
} from "../models/expense-draft-view";
import { ExpenseDraftEditorPage } from "../pages/expense-draft-editor-page";
import { ExpenseDraftPickerPage } from "../pages/expense-draft-picker-page";
import { ExpenseLinePage } from "../pages/expense-line-page";

type Props = ExpenseScreenProps & { creating: boolean; nextIdentifier: () => string };
export function ExpenseDraftEditorScreen(props: Props) {
  const { claimId = "" } = useParams(),
    [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const query = expenseClaimParameters(
    props.access.companyId,
    expenseClaimSearchFromRoute(parameters, props.access.companyId, today),
  ).toString();
  const path = props.creating ? "/expenses/claims/new" : `${expenseClaimPath(claimId)}/edit`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/expenses/claims?${query}`} replace />;
  if (!parameters.has("company")) return <Navigate to={`${path}?${query}`} replace />;
  return (
    <ExpenseDraftEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${path}`}
      {...props}
      id={claimId}
      today={today}
      query={query}
    />
  );
}
function ExpenseDraftEditorBinding(props: Props & { id: string; today: string; query: string }) {
  const id = useMemo(
    () => (props.creating ? props.nextIdentifier() : props.id),
    [props.creating, props.nextIdentifier, props.id],
  );
  const controller = useMemo(
    () =>
      new ExpenseDraftEditorController(
        props.expense,
        props.access,
        props.creating,
        id,
        props.nextIdentifier,
      ),
    [props.expense, props.access, props.creating, id, props.nextIdentifier],
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
  const form = useExpenseDraftForm(controller, state),
    view = useMemo(() => expenseDraftEditorView(state, props.locale), [state, props.locale]);
  return (
    <ExpenseDraftEditorPage
      state={state}
      form={form}
      view={view}
      creating={props.creating}
      companyName={props.companyName}
      locale={props.locale}
      backTo={`/expenses/claims?${props.query}`}
      savedTo={state.receipt ? `${expenseClaimPath(state.receipt.id)}?${props.query}` : null}
      onAdd={() => controller.addLine(props.today)}
      onEdit={controller.editLine}
      onRetry={controller.retry}
      picker={
        state.stage === "choosing" ? (
          <ExpenseEmployeeBinding {...props} controller={controller} />
        ) : null
      }
      panel={
        state.stage === "editing" && state.panel.kind === "line" && state.references ? (
          <ExpenseLineBinding
            key={state.panel.line.id}
            {...props}
            controller={controller}
            line={state.panel.line}
            creating={state.panel.creating}
            references={state.references}
            failure={state.entryFailure}
          />
        ) : null
      }
    />
  );
}
function ExpenseEmployeeBinding(
  props: ExpenseScreenProps & { controller: ExpenseDraftEditorController },
) {
  const state = useExpenseDraftEmployees(
      props.accountId,
      props.access,
      props.expense.loadDraftEmployees,
    ),
    text = expenseDraftMessages(props.locale);
  useEffect(() => {
    if (state.failure) props.controller.withdrawEvidence(state.failure);
  }, [props.controller, state.failure]);
  const rows =
    state.directory?.employees.items.map((employee) => ({
      id: employee.id,
      actionLabel: employee.legalName,
      cells: [
        employee.legalName,
        employee.employeeNumber,
        text[employee.status as "ACTIVE" | "PROBATION" | "ENDED" | "SUSPENDED"],
      ],
    })) ?? [];
  return (
    <ExpenseDraftPickerPage
      state={state}
      title={text.chooseEmployee}
      hint={text.employeeHint}
      searchLabel={text.searchEmployees}
      rows={rows}
      ready={state.directory !== null}
      hasNext={state.directory?.employees.nextCursor != null}
      columns={[
        { id: "name", label: text.employee },
        { id: "number", label: text.number },
        { id: "status", label: text.status },
      ]}
      locale={props.locale}
      onSelect={(id) => {
        const employee = state.directory?.employees.items.find((item) => item.id === id);
        if (employee && state.directory)
          props.controller.chooseEmployee(employee, state.directory.companyDate);
      }}
    />
  );
}
function ExpenseLineBinding(
  props: ExpenseScreenProps & {
    controller: ExpenseDraftEditorController;
    line: ExpenseLine;
    creating: boolean;
    references: ExpenseDraftReferences;
    failure: Failure | null;
  },
) {
  const form = useExpenseLineForm(props.controller, props.line, props.references),
    view = expenseLineSelectionView(
      form.categoryId,
      form.costCenterId,
      form.receiptIds,
      form.labels,
      props.locale,
    );
  const selected =
    form.panel === "category"
      ? [form.categoryId]
      : form.panel === "costCenter"
        ? form.costCenterId
          ? [form.costCenterId]
          : []
        : form.receiptIds;
  return (
    <ExpenseLinePage
      form={form}
      view={view}
      creating={props.creating}
      failure={props.failure}
      locale={props.locale}
      picker={
        form.panel !== "fields" ? (
          <ExpenseOptionsBinding
            key={`${form.panel}:${form.occurredOn.value}`}
            {...props}
            kind={form.panel}
            employmentId={props.references.employmentId}
            occurredOn={form.occurredOn.value}
            selected={selected}
            onSelect={form.select}
          />
        ) : null
      }
    />
  );
}
function ExpenseOptionsBinding(
  props: ExpenseScreenProps & {
    controller: ExpenseDraftEditorController;
    kind: ExpenseDraftOption["kind"];
    employmentId: string;
    occurredOn: string;
    selected: readonly string[];
    onSelect: (option: ExpenseDraftOption) => void;
  },
) {
  const state = useExpenseDraftOptions(
      props.accountId,
      props.access,
      props.expense,
      props.kind,
      props.employmentId,
      props.occurredOn,
    ),
    text = expenseDraftMessages(props.locale);
  useEffect(() => {
    if (state.failure) props.controller.withdrawEvidence(state.failure);
  }, [props.controller, state.failure]);
  const rows = expenseDraftOptionRows(state.page, props.selected, props.kind, props.locale);
  return (
    <ExpenseDraftPickerPage
      state={state}
      title={
        props.kind === "category"
          ? text.categories
          : props.kind === "costCenter"
            ? text.costCenters
            : text.receiptPicker
      }
      {...(props.kind === "costCenter" ? { searchLabel: text.searchCostCenters } : {})}
      rows={rows}
      ready={state.page !== null}
      hasNext={state.page?.nextCursor != null}
      columns={[
        { id: "name", label: text.nameColumn },
        { id: "details", label: text.detailsColumn },
      ]}
      locale={props.locale}
      onSelect={(id) => {
        const option = state.page?.items.find((item) => item.id === id);
        if (option && !rows.find((row) => row.id === id)?.actionDisabled) props.onSelect(option);
      }}
    />
  );
}
