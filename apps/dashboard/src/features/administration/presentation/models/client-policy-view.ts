import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ClientPolicyReview } from "../../domain/entities/client-policy-review";
import { companyModules } from "../../domain/entities/company-module";
import { clientPolicyMessages } from "../i18n/client-policy-messages";

export function clientPolicyView(review: ClientPolicyReview, locale: Locale) {
  const text = clientPolicyMessages(locale);
  const date = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "medium",
    timeZone: "UTC",
  });
  const number = new Intl.NumberFormat(locale);
  const { effective, latest } = review.settings;
  const selected = review.selected;
  return {
    effective: [
      {
        label: text.version,
        value: effective.version === null ? text.default : number.format(effective.version),
      },
      {
        label: text.latestVersion,
        value: latest === null ? text.none : number.format(latest.version),
      },
      { label: text.state, value: effective.maintenanceActive ? text.maintenance : text.available },
      { label: text.evaluated, value: date.format(new Date(effective.evaluatedAt)) },
      { label: text.validUntil, value: date.format(new Date(effective.validUntil)) },
    ],
    minimumBuilds: [
      {
        label: "Android",
        value:
          effective.minimumBuilds.android === 0
            ? text.anyBuild
            : number.format(effective.minimumBuilds.android),
      },
      {
        label: "iOS",
        value:
          effective.minimumBuilds.ios === 0
            ? text.anyBuild
            : number.format(effective.minimumBuilds.ios),
      },
      {
        label: "Web",
        value:
          effective.minimumBuilds.web === 0
            ? text.anyBuild
            : number.format(effective.minimumBuilds.web),
      },
    ],
    modules: companyModules.map((module) => ({
      id: module,
      cells: [
        text[module],
        effective.enabledModules.includes(module) ? text.enabled : text.disabled,
      ],
    })),
    selected:
      selected === null
        ? null
        : [
            { label: text.version, value: number.format(selected.version) },
            { label: text.activation, value: date.format(new Date(selected.activateAt)) },
            { label: text.recorded, value: date.format(new Date(selected.recordedAt)) },
            { label: text.actor, value: selected.actorId },
            { label: text.reason, value: selected.reason },
            {
              label: text.disabledModules,
              value:
                selected.disabledModules.length === 0
                  ? text.none
                  : selected.disabledModules.map((module) => text[module]).join(", "),
            },
            { label: "Android", value: number.format(selected.minimumBuilds.android) },
            { label: "iOS", value: number.format(selected.minimumBuilds.ios) },
            { label: "Web", value: number.format(selected.minimumBuilds.web) },
            {
              label: text.maintenanceStarts,
              value: selected.maintenance
                ? date.format(new Date(selected.maintenance.startsAt))
                : text.none,
            },
            {
              label: text.maintenanceEnds,
              value: selected.maintenance
                ? date.format(new Date(selected.maintenance.endsAt))
                : text.none,
            },
          ],
  };
}
export type ClientPolicyView = ReturnType<typeof clientPolicyView>;
