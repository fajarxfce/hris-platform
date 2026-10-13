import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Add20Regular, Save20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useExpenseDraftForm } from "../controllers/use-expense-draft-form";
import { expenseDraftFieldError, expenseDraftMessages } from "../i18n/expense-draft-messages";
import type { ExpenseDraftEditorState } from "../models/expense-draft-editor-state";
import type { expenseDraftEditorView } from "../models/expense-draft-view";
export function ExpenseDraftEditorPage(props: {
  state: ExpenseDraftEditorState;
  form: ReturnType<typeof useExpenseDraftForm>;
  view: ReturnType<typeof expenseDraftEditorView>;
  creating: boolean;
  locale: Locale;
  companyName: string;
  backTo: string;
  savedTo: string | null;
  onAdd: () => void;
  onEdit: (id: string) => void;
  onRetry: () => void;
  picker: ReactNode;
  panel: ReactNode;
}) {
  const { state, form, locale } = props,
    text = expenseDraftMessages(locale);
  return (
    <>
      <section
        className="app-report-content app-record-editor"
        aria-busy={state.stage === "loading" || state.stage === "saving"}
      >
        <AppPageHeader
          title={props.creating ? text.create : text.edit}
          context={props.companyName}
          actions={<Link to={props.backTo}>{text.back}</Link>}
        />
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.stage === "saving" && <AppLoading label={text.saving} />}
        {state.stage === "unavailable" && (
          <div>
            <AppButton onClick={form.refresh}>{messages(locale).retry}</AppButton>
          </div>
        )}
        {state.stage === "unconfirmed" && (
          <MessageBar intent="warning" layout="multiline" role="status">
            <MessageBarBody>
              {text.unconfirmed} <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
            </MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "conflict" && (
          <MessageBar intent="warning" layout="multiline" role="status">
            <MessageBarBody>
              {text.conflict} <AppButton onClick={form.refresh}>{text.reload}</AppButton>
            </MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "saved" && (
          <MessageBar intent="success" layout="multiline" role="status">
            <MessageBarBody>
              {text.saved} {props.savedTo && <Link to={props.savedTo}>{text.details}</Link>}
            </MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "choosing" && props.picker}
        {state.employee && (
          <>
            <AppPropertyList
              title={text.employee}
              items={[
                { label: text.employee, value: state.employee.legalName },
                { label: text.number, value: state.employee.employeeNumber },
              ]}
            />
            {state.original?.claim.status === "RETURNED" && (
              <MessageBar intent="info" layout="multiline">
                <MessageBarBody>{text.returned}</MessageBarBody>
              </MessageBar>
            )}
            {state.original && (
              <p className="app-muted">
                {text.basedOn}: {state.original.claim.version}
              </p>
            )}
            <form
              className="app-editor-form"
              onSubmit={form.submit}
              noValidate
              aria-label={props.creating ? text.create : text.edit}
            >
              <AppTextField
                label={text.name}
                {...form.title}
                maxLength={160}
                required
                readOnly={!form.editable}
                error={expenseDraftFieldError(state.failure?.fields.title, locale)}
              />
              <AppTextArea
                label={text.description}
                {...form.description}
                maxLength={2000}
                rows={2}
                readOnly={!form.editable}
                error={expenseDraftFieldError(state.failure?.fields.description, locale)}
              />
              <div className="app-form-actions">
                <AppButton
                  icon={<Add20Regular />}
                  onClick={props.onAdd}
                  disabled={!form.editable || state.lines.length >= 20}
                >
                  {text.add}
                </AppButton>
                <span className="app-muted">{text.lineLimit}</span>
              </div>
              {state.lines.length === 0 ? (
                <p role="status">{text.empty}</p>
              ) : (
                <AppResourceTable
                  title={text.items}
                  minWidth={800}
                  columns={[
                    { id: "date", label: text.occurredOn },
                    { id: "description", label: text.itemDescription },
                    { id: "category", label: text.category },
                    { id: "amount", label: text.amount, numeric: true },
                    { id: "receipts", label: text.receipts, numeric: true },
                  ]}
                  rows={props.view.rows}
                  action={{ label: text.editItem, onOpen: props.onEdit, disabled: !form.editable }}
                />
              )}
              <AppPropertyList
                title={text.total}
                items={[
                  { label: text.count, value: String(state.lines.length) },
                  { label: text.total, value: props.view.total },
                ]}
              />
              <AppTextArea
                label={text.reason}
                {...form.reason}
                maxLength={1000}
                rows={2}
                required
                readOnly={!form.editable}
                hint={text.reasonHint}
                error={expenseDraftFieldError(state.failure?.fields.reason, locale)}
              />
              <p className="app-muted">{text.policyHint}</p>
              <div className="app-form-actions">
                <AppButton
                  type="submit"
                  appearance="primary"
                  icon={<Save20Regular />}
                  disabled={!form.editable}
                >
                  {text.save}
                </AppButton>
              </div>
            </form>
          </>
        )}
      </section>
      {props.panel}
    </>
  );
}
