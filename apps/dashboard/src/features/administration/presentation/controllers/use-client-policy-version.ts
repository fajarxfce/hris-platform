import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { isClientPolicyVersion } from "../../domain/policies/client-settings-policy";

export function useClientPolicyVersion(
  version: string | null,
  locale: Locale,
  apply: (version: string | null) => void,
) {
  const values = useMemo(() => ({ version: version ?? "" }), [version]);
  const form = useForm<{ version: string }>({ values });
  const field = useController({
    name: "version",
    control: form.control,
    rules: { required: true, validate: isClientPolicyVersion },
  });
  return {
    input: field.field,
    error: field.fieldState.error
      ? failureMessage({ code: "invalid_revision", fields: {}, parameters: {} }, locale)
      : undefined,
    apply: form.handleSubmit((value) => apply(value.version)),
    latest: () => {
      form.reset({ version: "" });
      apply(null);
    },
  };
}
