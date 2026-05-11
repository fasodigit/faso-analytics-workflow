import { CommonModule } from '@angular/common';
import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
} from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { FormlyFieldConfig, FormlyModule } from '@ngx-formly/core';
import { DagNode } from './dag-node.model';
import { getFormlySchemaForKind } from './params-form-schema';

@Component({
  selector: 'fd-params-sidebar',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormlyModule],
  templateUrl: './params-sidebar.component.html',
})
export class ParamsSidebarComponent implements OnChanges {
  @Input() node: DagNode | null = null;
  @Output() paramsChange = new EventEmitter<Record<string, unknown>>();
  @Output() labelChange = new EventEmitter<string>();

  form = new FormGroup({});
  fields: FormlyFieldConfig[] = [];
  model: Record<string, unknown> = {};

  ngOnChanges(changes: SimpleChanges): void {
    if (!('node' in changes)) return;
    if (!this.node) {
      this.fields = [];
      this.model = {};
      this.form = new FormGroup({});
      return;
    }
    this.fields = getFormlySchemaForKind(this.node.kind);
    this.model = this.hydrateModel(this.node.paramsJson);
    this.form = new FormGroup({});
  }

  /**
   * Some form keys store comma-separated lists for arrays. Hydrate accordingly.
   */
  private hydrateModel(
    params: Record<string, unknown>,
  ): Record<string, unknown> {
    const copy: Record<string, unknown> = { ...params };
    if (Array.isArray(copy['groupBy'])) {
      copy['groupBy'] = (copy['groupBy'] as unknown[]).join(', ');
    }
    if (Array.isArray(copy['fields'])) {
      copy['fields'] = (copy['fields'] as unknown[]).join(', ');
    }
    return copy;
  }

  private dehydrateModel(
    model: Record<string, unknown>,
  ): Record<string, unknown> {
    const out: Record<string, unknown> = { ...model };
    if (typeof out['groupBy'] === 'string') {
      out['groupBy'] = (out['groupBy'] as string)
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0);
    }
    if (typeof out['fields'] === 'string') {
      out['fields'] = (out['fields'] as string)
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0);
    }
    if (typeof out['__raw'] === 'string' && (out['__raw'] as string).trim() !== '') {
      try {
        const parsed = JSON.parse(out['__raw'] as string) as Record<string, unknown>;
        delete out['__raw'];
        Object.assign(out, parsed);
      } catch {
        // Keep __raw on parse error; the parent serializer will surface the issue
      }
    } else {
      delete out['__raw'];
    }
    return out;
  }

  onModelChange(model: Record<string, unknown>): void {
    this.paramsChange.emit(this.dehydrateModel(model));
  }

  onLabelEdit(value: string): void {
    this.labelChange.emit(value);
  }
}
