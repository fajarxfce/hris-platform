import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowSync20Regular, Search20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelectableResourceTable } from "../../../../core/presentation/components/app-selectable-resource-table";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePayrollEmployeeSearch } from "../controllers/use-payroll-employee-search";
import type { usePayrollPeriodCreationForm } from "../controllers/use-payroll-period-creation-form";
import { payrollPeriodMessages } from "../i18n/payroll-period-messages";
import type { PayrollPeriodCreationState } from "../models/payroll-period-creation-state";
import type { payrollPeriodCreationView } from "../models/payroll-period-view";
export function PayrollPeriodCreationPage(props: {
  state: PayrollPeriodCreationState;
  view: ReturnType<typeof payrollPeriodCreationView>;
  form: ReturnType<typeof usePayrollPeriodCreationForm>;
  search: ReturnType<typeof usePayrollEmployeeSearch>;
  locale: Locale;
  companyName: string;
  backTo: string;
  savedTo: string | null;
  onToggle: (id: string, checked: boolean) => void;
  onTogglePage: (checked: boolean) => void;
  onRemove: (id: string) => void;
  onSelectedPage: (page: number) => void;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onEdit: () => void;
  onConfirm: () => void;
  onRetry: () => void;
}) {
  const { state, view, form, locale } = props,
    text = payrollPeriodMessages(locale);
  return (
    <section className="app-report-content app-record-editor" aria-busy={state.stage === "saving"}>
      <AppPageHeader
        title={state.review ? text.review : text.createTitle}
        headingRef={form.headingRef}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "saving" && <AppLoading label={text.saving} />}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed} <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.saved} {props.savedTo && <Link to={props.savedTo}>{text.open}</Link>}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "editing" && (
        <>
          <form
            id="payroll-period-create"
            className="app-editor-form"
            onSubmit={form.submit}
            aria-label={text.createTitle}
            noValidate
          >
            <div className="app-payroll-period-fields">
              <AppTextField
                label={text.earningsMonth}
                type="month"
                min="2024-01"
                max="2100-12"
                required
                {...form.earningsMonth}
                error={form.invalidMonth ? text.invalidMonth : undefined}
              />
              <AppTextField
                label={text.paymentDate}
                type="date"
                min="2024-01-01"
                max="2100-12-31"
                required
                {...form.plannedPaymentDate}
                error={form.invalidPayment ? text.invalidPayment : undefined}
              />
            </div>
            <p className="app-muted">{text.paymentHelp}</p>
            <AppTextArea
              label={text.reason}
              required
              rows={2}
              maxLength={1000}
              {...form.reason}
              error={form.invalidReason ? text.invalidReason : undefined}
            />
          </form>
          <section aria-busy={state.loading} className="app-report-content">
            <form className="app-resource-filters" onSubmit={props.search.submit}>
              <AppTextField
                label={text.search}
                {...props.search.field}
                maxLength={120}
                contentBefore={<Search20Regular />}
              />
              <AppButton type="submit">{text.searchAction}</AppButton>
              <AppButton
                appearance="subtle"
                icon={<ArrowSync20Regular />}
                onClick={props.onRefresh}
                disabled={state.loading}
              >
                {messages(locale).refresh}
              </AppButton>
            </form>
            <p className="app-muted">{text.limit}</p>
            <AppFailure failure={state.searchFailure} locale={locale} />
            {state.loading && <AppLoading label={messages(locale).loading} />}
            {state.page && (
              <>
                {view.rows.length ? (
                  <AppSelectableResourceTable
                    title={text.participants}
                    columns={[
                      { id: "number", label: text.employeeNumber },
                      { id: "name", label: text.name },
                    ]}
                    rows={view.rows}
                    selectLabel={text.select}
                    selectPageLabel={text.selectPage}
                    pageChecked={view.pageChecked}
                    onToggle={props.onToggle}
                    onTogglePage={props.onTogglePage}
                  />
                ) : (
                  <p role="status">{text.lookupEmpty}</p>
                )}
                <fieldset className="app-pagination" aria-label={text.employeePages}>
                  <AppButton onClick={props.onFirst} disabled={state.after === null}>
                    {text.first}
                  </AppButton>
                  <AppButton onClick={props.onNext} disabled={state.page.nextCursor === null}>
                    {text.next}
                  </AppButton>
                </fieldset>
              </>
            )}
          </section>
        </>
      )}
      {state.review && (
        <>
          <AppPropertyList title={text.review} items={view.properties} />
          <p>{text.reviewHelp}</p>
        </>
      )}
      {(state.stage === "editing" || state.review) && (
        <section className="app-report-content app-payroll-selection">
          <p role="status">
            {text.selectionCount}: <strong>{view.selectedCount}</strong>
          </p>
          {view.selectedRows.length ? (
            <AppResourceTable
              title={text.selected}
              columns={[
                { id: "number", label: text.employeeNumber },
                { id: "name", label: text.name },
              ]}
              rows={view.selectedRows}
              {...(state.stage === "editing"
                ? { action: { label: text.remove, onOpen: props.onRemove } }
                : {})}
            />
          ) : (
            <p>{text.selectionEmpty}</p>
          )}
          {view.selectedPages > 1 && (
            <fieldset className="app-pagination" aria-label={text.selectionPages}>
              <AppButton
                onClick={() => props.onSelectedPage(state.selectedPage - 1)}
                disabled={state.selectedPage === 0}
              >
                {text.previous}
              </AppButton>
              <span>
                {state.selectedPage + 1} / {view.selectedPages}
              </span>
              <AppButton
                onClick={() => props.onSelectedPage(state.selectedPage + 1)}
                disabled={state.selectedPage + 1 >= view.selectedPages}
              >
                {text.next}
              </AppButton>
            </fieldset>
          )}
          <p className="app-muted">{text.immutable}</p>
          {state.stage === "editing" && (
            <div className="app-form-actions">
              <AppButton
                type="submit"
                form="payroll-period-create"
                appearance="primary"
                disabled={state.selected.length === 0}
              >
                {text.review}
              </AppButton>
            </div>
          )}
          {state.stage === "reviewing" && (
            <div className="app-form-actions">
              <AppButton appearance="primary" onClick={props.onConfirm}>
                {text.confirm}
              </AppButton>
              <AppButton onClick={props.onEdit}>{text.edit}</AppButton>
            </div>
          )}
        </section>
      )}
    </section>
  );
}
