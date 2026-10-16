import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowClockwise20Regular, Edit20Regular, Save20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { RosterBulkFields } from "../components/roster-bulk-fields";
import { RosterBulkSelection } from "../components/roster-bulk-selection";
import type { useRosterBulkForm } from "../controllers/use-roster-bulk-form";
import { rosterBulkMessages } from "../i18n/roster-bulk-messages";
import type { RosterBulkState } from "../models/roster-bulk-state";
import type { rosterBulkView } from "../models/roster-bulk-view";

export function RosterBulkPage(props: {
  state: RosterBulkState;
  form: ReturnType<typeof useRosterBulkForm>;
  view: ReturnType<typeof rosterBulkView> | null;
  failureContext: string | null;
  formId: string;
  locale: Locale;
  companyName: string;
  backTo: string;
  shiftPicker: ReactNode;
  employeePicker: ReactNode;
  onRetry: () => void;
}) {
  const { state, form, view, locale } = props;
  const text = rosterBulkMessages(locale);
  return (
    <section
      className="app-roster-bulk"
      aria-busy={state.stage === "preparing" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.title}>
        {state.stage === "editing" && (
          <AppButton
            appearance="primary"
            type="submit"
            form={props.formId}
            disabled={form.cellCount > 200}
          >
            {text.review}
          </AppButton>
        )}
        {state.stage === "reviewing" && (
          <AppButton
            appearance="primary"
            icon={<Save20Regular />}
            type="submit"
            form={props.formId}
            disabled={state.review?.changeCount === 0}
          >
            {text.save}
          </AppButton>
        )}
        {(state.stage === "reviewing" || state.stage === "conflict") && (
          <AppButton appearance="subtle" icon={<Edit20Regular />} onClick={form.revise}>
            {text.revise}
          </AppButton>
        )}
        {(state.stage === "editing" ||
          state.stage === "unavailable" ||
          state.stage === "saved") && (
          <AppButton appearance="subtle" icon={<ArrowClockwise20Regular />} onClick={form.reload}>
            {text.reload}
          </AppButton>
        )}
        {state.stage === "unconfirmed" && (
          <AppButton appearance="primary" onClick={props.onRetry}>
            {text.retry}
          </AppButton>
        )}
        {state.stage === "preparing" && <AppLoading label={text.preparing} />}
        {state.stage === "saving" && <AppLoading label={text.saving} />}
      </AppCommandBar>
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={locale} />
        {props.failureContext && <p>{props.failureContext}</p>}
        {state.stage === "unconfirmed" && (
          <MessageBar intent="warning" role="status">
            <MessageBarBody>
              {text.unconfirmed}
              <div className="app-reference">
                {text.operation}: {state.operationId}
              </div>
            </MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "saved" && (
          <MessageBar intent="success" role="status">
            <MessageBarBody>{text.saved}</MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "editing" && (
          <>
            <RosterBulkFields form={form} formId={props.formId} locale={locale} />
            {props.shiftPicker}
            <RosterBulkSelection form={form} locale={locale} />
            {props.employeePicker}
          </>
        )}
        {view && state.stage !== "saved" && (
          <>
            <h2 ref={form.headingRef} tabIndex={-1}>
              {text.reviewTitle}
            </h2>
            <div className="app-roster-bulk-review">
              <AppPropertyList title={text.selection} items={view.properties} />
              <form
                id={props.formId}
                className="app-roster-bulk-reason"
                onSubmit={form.submit}
                noValidate
              >
                <AppTextArea
                  label={text.reason}
                  {...form.reason}
                  required
                  maxLength={1000}
                  readOnly={state.stage !== "reviewing"}
                  error={form.invalidReason ? text.reasonRequired : undefined}
                />
                <p>{text.reviewNote}</p>
                {state.review?.changeCount === 0 && <p role="status">{text.noChanges}</p>}
              </form>
            </div>
            <AppResourceTable
              title={text.entries}
              columns={view.columns}
              rows={view.rows}
              minWidth={780}
            />
          </>
        )}
      </div>
    </section>
  );
}
