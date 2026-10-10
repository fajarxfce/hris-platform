import { Field, Textarea, type TextareaProps } from "@fluentui/react-components";
import { forwardRef } from "react";

type Props = Omit<TextareaProps, "textarea"> & {
  label: string;
  error?: string | undefined;
  hint?: string | undefined;
};
export const AppTextArea = forwardRef<HTMLTextAreaElement, Props>(function AppTextArea(
  { label, error, hint, ...input },
  ref,
) {
  return (
    <Field
      label={label}
      validationState={error ? "error" : "none"}
      validationMessage={error ?? null}
      hint={hint ?? null}
    >
      <Textarea {...input} textarea={{ ref }} />
    </Field>
  );
});
