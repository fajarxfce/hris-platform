import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { announcementEditorMessages } from "../i18n/announcement-editor-messages";
import { announcementMessages } from "../i18n/announcement-messages";
import type { AnnouncementListState } from "../models/announcement-list-state";
import type { announcementRows } from "../models/announcement-view";

export function AnnouncementsPage({
  state,
  rows,
  history,
  companyName,
  locale,
  backTo,
  createTo,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: AnnouncementListState;
  rows: ReturnType<typeof announcementRows>;
  history: boolean;
  companyName: string;
  locale: Locale;
  backTo: string | null;
  createTo: string | null;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = announcementMessages(locale);
  const title = history ? text.history : text.title;
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={title}
        context={companyName}
        actions={
          <>
            {backTo && <Link to={backTo}>{text.current}</Link>}
            {createTo && <Link to={createTo}>{announcementEditorMessages(locale).create}</Link>}
            <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        {state.stage === "loading" && !state.page && (
          <AppLoading label={messages(locale).loading} />
        )}
        {state.page &&
          (rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={title}
              columns={[
                { id: "title", label: text.subject },
                { id: "status", label: text.status },
                { id: "audience", label: text.audience },
                { id: "version", label: text.version, numeric: true },
                { id: "recorded", label: text.recordedAt },
              ]}
              rows={rows}
              action={{ label: text.view, onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.pages}>
          <AppButton disabled={firstPage || state.stage === "loading"} onClick={onFirst}>
            {text.first}
          </AppButton>
          <AppButton
            disabled={state.page?.nextCursor == null || state.stage === "loading"}
            onClick={onNext}
          >
            {text.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
