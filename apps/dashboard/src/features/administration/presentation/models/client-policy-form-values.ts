import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { ClientPolicyChange } from "../../domain/entities/client-policy-change";
import type { ClientPolicySettings } from "../../domain/entities/client-policy-settings";
import type { CompanyModule } from "../../domain/entities/company-module";

export type ClientPolicyFormValues = {
  activation: "IMMEDIATE" | "SCHEDULED";
  activateAt: string;
  disabledModules: CompanyModule[];
  android: string;
  ios: string;
  web: string;
  maintenanceEnabled: boolean;
  maintenanceStarts: string;
  maintenanceEnds: string;
  reason: string;
};

/** Start from the configured head, preserving UTC precision even when its policy is not effective. */
export function clientPolicyFormValues(
  settings: ClientPolicySettings | null,
): ClientPolicyFormValues {
  const head = settings?.latest;
  const activates = head ? utcInstantMicroseconds(head.activateAt) : null;
  const evaluated = settings ? utcInstantMicroseconds(settings.effective.evaluatedAt) : null;
  return {
    activation:
      activates !== null && evaluated !== null && activates > evaluated ? "SCHEDULED" : "IMMEDIATE",
    activateAt: head?.activateAt ?? "",
    disabledModules: [...(head?.disabledModules ?? [])],
    android: String(head?.minimumBuilds.android ?? 0),
    ios: String(head?.minimumBuilds.ios ?? 0),
    web: String(head?.minimumBuilds.web ?? 0),
    maintenanceEnabled: head?.maintenance !== null && head?.maintenance !== undefined,
    maintenanceStarts: head?.maintenance?.startsAt ?? "",
    maintenanceEnds: head?.maintenance?.endsAt ?? "",
    reason: "",
  };
}

/** Form mechanics produce domain values; the use case owns their validation. */
export function clientPolicyChangeFromFields(
  values: ClientPolicyFormValues,
): Omit<ClientPolicyChange, "expectedVersion"> {
  return {
    activateAt: values.activation === "IMMEDIATE" ? null : values.activateAt.trim(),
    disabledModules: values.disabledModules,
    minimumBuilds: {
      android: values.android.trim() === "" ? Number.NaN : Number(values.android),
      ios: values.ios.trim() === "" ? Number.NaN : Number(values.ios),
      web: values.web.trim() === "" ? Number.NaN : Number(values.web),
    },
    maintenance: values.maintenanceEnabled
      ? {
          startsAt: values.maintenanceStarts.trim(),
          endsAt: values.maintenanceEnds.trim(),
        }
      : null,
    reason: values.reason,
  };
}
