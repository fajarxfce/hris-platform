import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useAudienceGroups } from "../controllers/use-audience-groups";
import { audienceGroupMessages } from "../i18n/audience-group-messages";
import type { audienceGroupRows } from "../models/audience-group-view";

export function AudienceGroupsPage(props: {
  state: ReturnType<typeof useAudienceGroups>;
  rows: ReturnType<typeof audienceGroupRows>;
  locale: Locale;
  companyName: string;
  createTo: string;
  announcementsTo: string;
  firstPage: boolean;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = audienceGroupMessages(props.locale);
  return (
    <section aria-busy={props.state.loading}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <>
            <Link to={props.announcementsTo}>{text.announcements}</Link>
            <Link to={props.createTo}>{text.create}</Link>
            <AppButton disabled={props.state.loading} onClick={props.state.refresh}>
              {messages(props.locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={props.state.failure} locale={props.locale} />
      <div className="app-report-content">
        {props.state.loading && <AppLoading label={messages(props.locale).loading} />}
        {props.state.page &&
          (props.rows.length ? (
            <AppResourceTable
              title={text.title}
              columns={[
                { id: "name", label: text.name },
                { id: "status", label: text.status },
                { id: "members", label: text.members, numeric: true },
                { id: "version", label: text.version, numeric: true },
                { id: "recorded", label: text.recorded },
              ]}
              rows={props.rows}
              action={{ label: text.view, onOpen: props.onOpen }}
            />
          ) : (
            <p role="status">{text.empty}</p>
          ))}
        <nav className="app-form-actions" aria-label={text.pages}>
          <AppButton disabled={props.state.loading || props.firstPage} onClick={props.onFirst}>
            {text.first}
          </AppButton>
          <AppButton
            disabled={props.state.loading || !props.state.page?.nextCursor}
            onClick={props.onNext}
          >
            {text.next}
          </AppButton>
        </nav>
      </div>
    </section>
  );
}
