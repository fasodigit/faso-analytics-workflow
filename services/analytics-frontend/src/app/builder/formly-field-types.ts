import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import {
  FieldArrayType,
  FieldType,
  FieldTypeConfig,
  FormlyModule,
} from '@ngx-formly/core';

abstract class BaseFieldType extends FieldType<FieldTypeConfig> {
  // The [formControl] directive requires FormControl, FieldType exposes
  // AbstractControl. Cast once to keep templates type-safe.
  get formCtl(): FormControl {
    return this.formControl;
  }

  prop(name: string): unknown {
    return (this.props as unknown as Record<string, unknown>)[name];
  }
}

@Component({
  selector: 'fd-formly-input',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  template: `
    <label class="fd-field">
      <span class="fd-label">{{ props.label }}</span>
      <input
        [type]="$any(prop('type')) || 'text'"
        [formControl]="formCtl"
        [placeholder]="props.placeholder || ''"
        class="fd-input"
      />
    </label>
  `,
})
export class FdInputFieldComponent extends BaseFieldType {}

@Component({
  selector: 'fd-formly-textarea',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  template: `
    <label class="fd-field">
      <span class="fd-label">{{ props.label }}</span>
      <textarea
        [formControl]="formCtl"
        [placeholder]="props.placeholder || ''"
        [rows]="$any(prop('rows')) || 3"
        class="fd-input fd-textarea"
      ></textarea>
    </label>
  `,
})
export class FdTextareaFieldComponent extends BaseFieldType {}

@Component({
  selector: 'fd-formly-select',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  template: `
    <label class="fd-field">
      <span class="fd-label">{{ props.label }}</span>
      <select [formControl]="formCtl" class="fd-input">
        <option *ngFor="let o of optionsArr" [ngValue]="o.value">
          {{ o.label }}
        </option>
      </select>
    </label>
  `,
})
export class FdSelectFieldComponent extends BaseFieldType {
  get optionsArr(): Array<{ value: unknown; label: string }> {
    const raw = this.prop('options') as
      | Array<{ value: unknown; label: string } | string>
      | undefined;
    return (raw ?? []).map((o) =>
      typeof o === 'string' ? { value: o, label: o } : o,
    );
  }
}

@Component({
  selector: 'fd-formly-repeat',
  standalone: true,
  imports: [CommonModule, FormlyModule],
  template: `
    <div class="fd-repeat">
      <div class="fd-label">{{ props.label }}</div>
      <div
        *ngFor="let f of field.fieldGroup; let i = index"
        class="fd-repeat-row"
      >
        <formly-field [field]="f"></formly-field>
        <button type="button" class="fd-btn-icon" (click)="remove(i)">×</button>
      </div>
      <button type="button" class="fd-btn-add" (click)="add()">
        + Add row
      </button>
    </div>
  `,
})
export class FdRepeatFieldComponent extends FieldArrayType {}

export const FD_FORMLY_TYPES = [
  { name: 'input', component: FdInputFieldComponent },
  { name: 'textarea', component: FdTextareaFieldComponent },
  { name: 'select', component: FdSelectFieldComponent },
  { name: 'repeat', component: FdRepeatFieldComponent },
];
