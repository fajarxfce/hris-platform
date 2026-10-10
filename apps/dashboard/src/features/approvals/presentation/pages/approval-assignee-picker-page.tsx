import type { FormEventHandler } from "react";
import type { ControllerRenderProps } from "react-hook-form";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDialog } from "../../../../core/presentation/components/app-dialog";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { approvalMessages } from "../i18n/approval-messages";
import { approvalTemplateMessages } from "../i18n/approval-template-messages";
import type { ApprovalAssigneePickerState } from "../models/approval-assignee-picker-state";

export function ApprovalAssigneePickerPage({
  state,
  rows,
  query,
  locale,
  onSearch,
  onFirst,
  onNext,
  onRefresh,
  onSelect,
  onDismiss,
}: {
  state: ApprovalAssigneePickerState;
  rows: readonly { id: string; actionLabel: string; cells: string[] }[];
  query: ControllerRenderProps<{ query: string }, "query">;
  locale: Locale;
  onSearch: FormEventHandler<HTMLFormElement>;
  onFirst: () => void;
  onNext: () => void;
  onRefresh: () => void;
  onSelect: (id: string) => void;
  onDismiss: () => void;
}) {
  const text = approvalTemplateMessages(locale);
  const approval = approvalMessages(locale);
  return (
    <AppDialog open title={text.selectApprover} busy={false} onDismiss={onDismiss}>
      <div className="app-report-content" aria-busy={state.stage === "loading"}>
        <form className="app-editor-form" onSubmit={onSearch} noValidate>
          <AppTextField label={text.search} {...query} maxLength={120} />
          <AppButton type="submit">{text.searchAction}</AppButton>
        </form>
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.stage === "unavailable" && (
          <AppButton onClick={onRefresh}>{messages(locale).retry}</AppButton>
        )}
        {state.page &&
          (rows.length > 0 ? (
            <AppResourceTable
              title={text.selectApprover}
              columns={[
                { id: "name", label: text.name },
                { id: "account", label: text.account },
              ]}
              rows={rows}
              action={{ label: text.select, onOpen: onSelect }}
            />
          ) : (
            <p role="status">{text.noAssignees}</p>
          ))}
        <div className="app-form-actions">
          <AppButton onClick={onFirst} disabled={state.after === null || state.stage === "loading"}>
            {approval.first}
          </AppButton>
          <AppButton
            onClick={onNext}
            disabled={!state.page?.nextCursor || state.stage === "loading"}
          >
            {approval.next}
          </AppButton>
          <AppButton onClick={onDismiss}>{text.close}</AppButton>
        </div>
      </div>
    </AppDialog>
  );
}
