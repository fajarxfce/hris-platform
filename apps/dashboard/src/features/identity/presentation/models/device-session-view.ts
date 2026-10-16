import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { DeviceSession } from "../../domain/entities/device-session";
import type { ManagedAccount } from "../../domain/entities/managed-account";
import { deviceSessionMessages } from "../i18n/device-session-messages";

export function deviceSessionRows(sessions: readonly DeviceSession[], locale: Locale) {
  const time = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" });
  return sessions.map((session) => ({
    id: session.id,
    actionLabel: `${session.deviceName}, ${time.format(new Date(session.createdAt))}`,
    cells: [
      session.deviceName,
      time.format(new Date(session.createdAt)),
      time.format(new Date(session.expiresAt)),
    ],
  }));
}
export function deviceSessionProperties(
  session: DeviceSession | null,
  locale: Locale,
  account?: ManagedAccount | null,
) {
  if (!session) return [];
  const text = deviceSessionMessages(locale);
  const time = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" });
  return [
    ...(account
      ? [
          { label: text.account, value: account.displayName },
          { label: text.accountEmail, value: account.email },
        ]
      : []),
    { label: text.device, value: session.deviceName },
    { label: text.created, value: time.format(new Date(session.createdAt)) },
    { label: text.expires, value: time.format(new Date(session.expiresAt)) },
  ];
}
