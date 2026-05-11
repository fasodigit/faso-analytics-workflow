import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

export type SubProject =
  | 'VOUCHERS'
  | 'E_TICKET'
  | 'ETAT_CIVIL'
  | 'SOGESY'
  | 'HOSPITAL'
  | 'FASO_KALAN'
  | 'ALT_MISSION'
  | 'E_SCHOOL';

export interface ToolbarMetadata {
  name: string;
  subProject: SubProject;
  semver: string;
}

export type ToolbarAction =
  | 'new'
  | 'load-example'
  | 'validate'
  | 'export'
  | 'simulate';

@Component({
  selector: 'fd-toolbar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './toolbar.component.html',
})
export class ToolbarComponent {
  @Input() metadata: ToolbarMetadata = {
    name: 'mon-workflow',
    subProject: 'VOUCHERS',
    semver: '1.0.0-draft.1',
  };

  @Output() metadataChange = new EventEmitter<ToolbarMetadata>();
  @Output() action = new EventEmitter<{
    type: ToolbarAction;
    payload?: string;
  }>();

  readonly subProjects: SubProject[] = [
    'VOUCHERS',
    'E_TICKET',
    'ETAT_CIVIL',
    'SOGESY',
    'HOSPITAL',
    'FASO_KALAN',
    'ALT_MISSION',
    'E_SCHOOL',
  ];

  readonly examples = [
    { id: '01-bar-vertical', label: '01 — Bar vertical PAGSI/région' },
    { id: '02-bar-horizontal', label: '02 — Bar horizontal Top 10 communes' },
    { id: '03-bar-grouped', label: '03 — Bar grouped région × céréale' },
    { id: '04-donut', label: '04 — Donut distribution statuts' },
    { id: '05-half-donut', label: '05 — Half donut RESUREP espèces' },
    { id: '06-combo-dual-axis', label: '06 — Combo dual axis SONAGESS' },
    { id: '07-line-multi', label: '07 — Line multi evolution' },
    { id: '08-choropleth', label: '08 — Choropleth BF coverage' },
  ];

  selectedExample = '03-bar-grouped';

  emitMeta(): void {
    this.metadataChange.emit({ ...this.metadata });
  }

  onAction(type: ToolbarAction, payload?: string): void {
    this.action.emit({ type, payload });
  }
}
