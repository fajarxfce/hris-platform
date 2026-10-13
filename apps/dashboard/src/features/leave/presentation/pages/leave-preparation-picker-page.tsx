import { ArrowClockwise20Regular, Search20Regular } from "@fluentui/react-icons";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useLeavePreparationEmployees } from "../controllers/use-leave-preparation-employees";
import { leaveCreationMessages } from "../i18n/leave-creation-messages";

export function LeavePreparationPickerPage(props: {
  title: string;
  locale: Locale;
  loading: boolean;
  failure: Failure | null;
  ready: boolean;
  firstPage: boolean;
  hasNext: boolean;
  search?: Pick<ReturnType<typeof useLeavePreparationEmployees>, "input" | "submit">;
  columns: readonly Readonly<{ id: string; label: string }>[];
  rows: readonly Readonly<{
    id: string;
    actionLabel?: string;
    actionDisabled?: boolean;
    cells: readonly string[];
  }>[];
  onFirst: () => void;
  onNext: () => void;
  onRefresh: () => void;
  onSelect: (id: string) => void;
  onClose: () => void;
}) {
  const text = leaveCreationMessages(props.locale);
  return (
    <AppDetailsPanel
      open
      title={props.title}
      closeLabel={text.close}
      onClose={props.onClose}
      width={560}
    >
      <section className="app-report-content" aria-busy={props.loading}>
        {props.search && (
          <form className="app-form-actions" onSubmit={props.search.submit}>
            <AppTextField
              label={text.searchEmployees}
              type="search"
              contentBefore={<Search20Regular />}
              maxLength={120}
              {...props.search.input}
            />
            <AppButton type="submit">{text.search}</AppButton>
          </form>
        )}
        <AppFailure failure={props.failure} locale={props.locale} />
        {props.failure && (
          <AppButton
            appearance="subtle"
            icon={<ArrowClockwise20Regular />}
            onClick={props.onRefresh}
          >
            {messages(props.locale).retry}
          </AppButton>
        )}
        {props.loading && <AppLoading label={messages(props.locale).loading} />}
        {props.ready &&
          !props.loading &&
          (props.rows.length ? (
            <AppResourceTable
              title={props.title}
              columns={props.columns}
              rows={props.rows}
              minWidth={320}
              action={{ label: text.select, onOpen: props.onSelect }}
            />
          ) : (
            <p role="status">{text.noResults}</p>
          ))}
        <fieldset className="app-pagination" aria-label={text.pages}>
          <AppButton
            appearance="subtle"
            onClick={props.onFirst}
            disabled={props.firstPage || props.loading}
          >
            {text.first}
          </AppButton>
          <AppButton
            appearance="subtle"
            onClick={props.onNext}
            disabled={!props.hasNext || props.loading}
          >
            {text.next}
          </AppButton>
        </fieldset>
      </section>
    </AppDetailsPanel>
  );
}
