import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { announcementMessages } from "../i18n/announcement-messages";
import type { AnnouncementState } from "../models/announcement-state";
import type { announcementView } from "../models/announcement-view";

export function AnnouncementPage({
  state,
  properties,
  companyName,
  timezone,
  locale,
  backTo,
  historyTo,
  currentTo,
  jobTo,
  onRefresh,
}: {
  state: AnnouncementState;
  properties: ReturnType<typeof announcementView> | null;
  companyName: string;
  timezone: string;
  locale: Locale;
  backTo: string;
  historyTo: string;
  currentTo: string | null;
  jobTo: string | null;
  onRefresh: () => void;
}) {
  const text = announcementMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.announcement?.title ?? text.details}
        context={companyName}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            {currentTo && <Link to={currentTo}>{text.current}</Link>}
            {state.announcement && <Link to={historyTo}>{text.history}</Link>}
            {jobTo && <Link to={jobTo}>{text.job}</Link>}
            <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && !state.announcement && (
        <AppLoading label={messages(locale).loading} />
      )}
      {state.announcement && properties && (
        <div className="app-report-content">
          {currentTo && <Text role="status">{text.historyNotice}</Text>}
          <section className="app-resource-panel" aria-label={text.content}>
            <h2>{text.content}</h2>
            <p className="app-announcement-body">{state.announcement.body}</p>
          </section>
          <AppPropertyList title={`${text.overview} · ${timezone}`} items={properties} />
          <section className="app-resource-panel" aria-label={text.reason}>
            <h2>{text.reason}</h2>
            <p className="app-announcement-body">{state.announcement.reason}</p>
          </section>
        </div>
      )}
    </section>
  );
}
