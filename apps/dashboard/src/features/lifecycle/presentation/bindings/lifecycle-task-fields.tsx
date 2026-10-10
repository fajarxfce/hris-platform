import { memo } from "react";
import { type Control, useController } from "react-hook-form";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { lifecycleMessages } from "../i18n/lifecycle-messages";
import type { LifecycleTemplateFormValues } from "../models/lifecycle-template-form-values";

/** Subscribes only to this task's controls; field-array IDs retain row ownership. */
export const LifecycleTaskFields = memo(function LifecycleTaskFields({
  control,
  index,
  editable,
  canRemove,
  locale,
  onRemove,
}: {
  control: Control<LifecycleTemplateFormValues>;
  index: number;
  editable: boolean;
  canRemove: boolean;
  locale: Locale;
  onRemove: (index: number) => void;
}) {
  const key = useController({ name: `tasks.${index}.key`, control });
  const title = useController({ name: `tasks.${index}.title`, control });
  const dueDays = useController({ name: `tasks.${index}.dueDays`, control });
  const required = useController({ name: `tasks.${index}.required`, control });
  const text = lifecycleMessages(locale);
  return (
    <fieldset className="app-editor-section app-lifecycle-task">
      <legend>
        {text.task} {index + 1}
      </legend>
      <div className="app-editor-fields">
        <AppTextField
          label={text.key}
          {...key.field}
          required
          maxLength={48}
          readOnly={!editable}
          hint={text.keyHint}
        />
        <AppTextField
          label={text.taskTitle}
          {...title.field}
          required
          maxLength={160}
          readOnly={!editable}
        />
        <AppTextField
          label={text.dueDays}
          {...dueDays.field}
          type="number"
          required
          min={-90}
          max={365}
          step={1}
          readOnly={!editable}
        />
        <AppCheckbox
          label={text.required}
          checked={required.field.value}
          ref={required.field.ref}
          onBlur={required.field.onBlur}
          disabled={!editable}
          onChange={(_, data) => required.field.onChange(data.checked === true)}
        />
      </div>
      <div className="app-form-actions">
        <AppButton disabled={!canRemove} onClick={() => onRemove(index)}>
          {text.removeTask}
        </AppButton>
      </div>
    </fieldset>
  );
});
