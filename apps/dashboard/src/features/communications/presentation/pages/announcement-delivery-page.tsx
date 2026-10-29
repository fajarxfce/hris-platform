import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { announcementDeliveryMessages } from "../i18n/announcement-delivery-messages";
import type { AnnouncementDeliveryState } from "../models/announcement-delivery-state";
export function AnnouncementDeliveryPage(props: {
  state: AnnouncementDeliveryState;
  locale: Locale;
  timezone: string;
  companyName: string;
  backTo: string;
  onRefresh: () => void;
}) {
  const text = announcementDeliveryMessages(props.locale),
    value = props.state.delivery;
  const number = new Intl.NumberFormat(props.locale);
  const recipients = [
    "recipientCount",
    "pending",
    "processing",
    "completed",
    "failed",
    "superseded",
    "unavailableRecipients",
  ] as const;
  const devices = ["processedDevices", "acceptedDevices", "rejectedDevices"] as const;
  return (
    <section aria-busy={props.state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <>
            <Link to={props.backTo}>{text.back}</Link>
            <AppButton disabled={props.state.stage === "loading"} onClick={props.onRefresh}>
              {messages(props.locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={props.state.failure} locale={props.locale} />
      {props.state.stage === "loading" && <AppLoading label={messages(props.locale).loading} />}
      <div className="app-report-content">
        <p>{text.notice}</p>
        {value && (
          <>
            <AppPropertyList
              title={text.recipients}
              items={recipients.map((key) => ({
                label: text[key],
                value: number.format(value[key]),
              }))}
            />
            <AppPropertyList
              title={text.devices}
              items={devices.map((key) => ({ label: text[key], value: number.format(value[key]) }))}
            />
            <p>{text.acceptance}</p>
            {value.unavailableRecipients > 0 && <p role="status">{text.retention}</p>}
            <AppPropertyList
              title={text.observedAt}
              items={[
                {
                  label: props.timezone,
                  value: new Intl.DateTimeFormat(props.locale, {
                    timeZone: props.timezone,
                    dateStyle: "medium",
                    timeStyle: "medium",
                  }).format(new Date(value.observedAt)),
                },
              ]}
            />
          </>
        )}
      </div>
    </section>
  );
}
