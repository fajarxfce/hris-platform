import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { overtimeActionSnapshot } from "../../domain/entities/overtime-action-snapshot";
import type { OvertimeRequestDetails } from "../../domain/entities/overtime-request-details";
import { canPerformOvertimeAction } from "../../domain/policies/overtime-action-policy";
import { overtimeActionMessages } from "../i18n/overtime-action-messages";
import {
  type OvertimeActionIntent,
  overtimeActionIntents,
  overtimeIntentAction,
} from "./overtime-action-intent";
import { overtimeRequestPath } from "./overtime-route";

export function overtimeActionCopy(intent: OvertimeActionIntent, locale: Locale) {
  const text = overtimeActionMessages(locale);
  return { title: text[intent], hint: text[`${intent}Hint`], saved: text[`${intent}Saved`] };
}

export function overtimeActionLinks(
  details: OvertimeRequestDetails | null,
  access: CompanyAccess,
  query: string,
  locale: Locale,
) {
  if (!details) return [];
  const review = overtimeActionSnapshot(details),
    text = overtimeActionMessages(locale);
  return overtimeActionIntents
    .filter((intent) => canPerformOvertimeAction(access, review, overtimeIntentAction[intent]))
    .map((intent) => ({
      intent,
      label: text[intent],
      to: `${overtimeRequestPath(review.id)}/${intent}?${query}`,
    }));
}
