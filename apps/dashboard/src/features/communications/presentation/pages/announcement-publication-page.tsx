import { Link } from "react-router-dom";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { AnnouncementCommandKind } from "../../domain/entities/announcement-command";
import type { useAnnouncementPreview } from "../controllers/use-announcement-preview";
import type { useAnnouncementReview } from "../controllers/use-announcement-review";
import { announcementPublicationMessages } from "../i18n/announcement-publication-messages";
import type {
  announcementJobView,
  announcementPreviewView,
} from "../models/announcement-publication-view";
import type { announcementView } from "../models/announcement-view";

export function AnnouncementPublicationPage(props: {
  state: ReturnType<typeof useAnnouncementReview>;
  preview: ReturnType<typeof useAnnouncementPreview>;
  properties: ReturnType<typeof announcementView>;
  jobProperties: ReturnType<typeof announcementJobView>;
  previewProperties: ReturnType<typeof announcementPreviewView>;
  jobFailure: Failure | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  jobTo: string | null;
  active: boolean;
  actions: readonly Readonly<{ action: AnnouncementCommandKind; to: string }>[];
}) {
  const text = announcementPublicationMessages(props.locale);
  return (
    <section aria-busy={props.state.loading}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <>
            <Link to={props.backTo}>{text.back}</Link>
            {props.actions.map((item) => (
              <Link key={item.action} to={item.to}>
                {text[item.action]}
              </Link>
            ))}
            {props.jobTo && <Link to={props.jobTo}>{text.job}</Link>}
            <AppButton disabled={props.state.loading} onClick={props.state.refresh}>
              {messages(props.locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={props.state.failure} locale={props.locale} />
      <div className="app-report-content">
        {props.state.loading && <AppLoading label={messages(props.locale).loading} />}
        {props.state.review && (
          <>
            <h2>{props.state.review.announcement.title}</h2>
            <p className="app-announcement-body">{props.state.review.announcement.body}</p>
            <AppPropertyList title={text.title} items={props.properties} />
            {props.state.review.publicationJob && (
              <>
                <AppPropertyList title={text.job} items={props.jobProperties} />
                <AppFailure failure={props.jobFailure} locale={props.locale} />
                {props.active && <p>{text.stopped}</p>}
              </>
            )}
            {props.preview.available && (
              <section className="app-report-content" aria-busy={props.preview.loading}>
                <AppButton disabled={props.preview.loading} onClick={props.preview.refresh}>
                  {text.preview}
                </AppButton>
                <AppFailure failure={props.preview.failure} locale={props.locale} />
                {props.preview.loading && <AppLoading label={messages(props.locale).loading} />}
                {props.preview.preview && (
                  <AppPropertyList title={text.audience} items={props.previewProperties} />
                )}
                <p>{text.previewNotice}</p>
              </section>
            )}
          </>
        )}
      </div>
    </section>
  );
}
