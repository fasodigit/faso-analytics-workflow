import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { ColDef, GridOptions } from 'ag-grid-community';
import {
  ClientSideRowModelModule,
  ModuleRegistry,
} from 'ag-grid-community';
import {
  PivotTableEncoding,
  Visualization,
  WorkflowDefinition,
} from './workflow-definition.model';

ModuleRegistry.registerModules([ClientSideRowModelModule]);

/**
 * Pivot table renderer powered by ag-grid Community.
 *
 * NOTE — pivotMode is an ag-grid Enterprise feature. Community Edition
 * allows the API flag (`pivotMode: true`) but the actual row-group/pivot
 * pipeline requires Enterprise modules. Phase 3 ships a pragmatic
 * fallback: pre-aggregated input rows (one row per rowFields × columnFields
 * tuple) with columnDefs derived dynamically from the encoding. Phase 4
 * will swap this for the real pivot pipeline.
 */
@Component({
  selector: 'app-pivot-table-renderer',
  standalone: true,
  imports: [CommonModule, AgGridAngular],
  template: `
    <div class="chart-wrapper">
      @if (title) {
        <h3 class="pivot-title">{{ title }}</h3>
      }
      @if (subtitle) {
        <div class="pivot-subtitle">{{ subtitle }}</div>
      }
      <ag-grid-angular
        class="ag-theme-alpine pivot-grid"
        [rowData]="rowData"
        [columnDefs]="columnDefs"
        [gridOptions]="gridOptions"
      ></ag-grid-angular>
      <div class="pivot-footer">
        Phase 3 — pivotMode flag enabled (Community fallback; full pivot
        engine is Enterprise, deferred to Phase 4).
      </div>
    </div>
  `,
  styles: [
    `
      :host { display: block; width: 100%; }
      .chart-wrapper {
        background: #fff;
        border: 1px solid #e3e3e8;
        border-radius: 8px;
        padding: 16px;
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
      }
      .pivot-title { margin: 0 0 4px 0; font-size: 1rem; font-weight: 600; }
      .pivot-subtitle { font-size: 0.85rem; color: #777; margin-bottom: 8px; }
      .pivot-grid { width: 100%; height: 420px; }
      .pivot-footer {
        margin-top: 8px;
        font-size: 0.75rem;
        color: #999;
        font-style: italic;
      }
    `,
  ],
})
export class PivotTableRendererComponent implements OnChanges {
  @Input({ required: true }) workflowDef!: WorkflowDefinition;
  @Input() rows: Array<Record<string, string | number>> = [];

  title?: string;
  subtitle?: string;
  rowData: Array<Record<string, string | number>> = [];
  columnDefs: ColDef[] = [];
  gridOptions: GridOptions = {
    pivotMode: true,
    defaultColDef: {
      sortable: true,
      resizable: true,
      filter: true,
      minWidth: 100,
    },
    domLayout: 'normal',
    pagination: false,
    animateRows: true,
  };

  ngOnChanges(_changes: SimpleChanges): void {
    const viz = this.workflowDef?.spec?.visualizations?.[0];
    if (!viz) {
      this.columnDefs = [];
      this.rowData = [];
      return;
    }
    this.title = viz.title;
    this.subtitle = viz.subtitle;
    this.rowData = this.rows;
    this.columnDefs = this.buildColumnDefs(viz);
  }

  private buildColumnDefs(viz: Visualization): ColDef[] {
    const enc = viz.encoding as unknown as PivotTableEncoding;
    const rowFields = Array.isArray(enc.rowFields) ? enc.rowFields : [];
    const columnFields = Array.isArray(enc.columnFields) ? enc.columnFields : [];
    const valueField = enc.valueField;
    const defs: ColDef[] = [];
    for (const rf of rowFields) {
      defs.push({
        field: rf,
        headerName: this.humanize(rf),
        rowGroup: true,
        enableRowGroup: true,
      });
    }
    for (const cf of columnFields) {
      defs.push({
        field: cf,
        headerName: this.humanize(cf),
        pivot: true,
        enablePivot: true,
      });
    }
    if (valueField) {
      defs.push({
        field: valueField,
        headerName: this.humanize(valueField),
        aggFunc: (enc.aggregation ?? 'SUM').toLowerCase(),
        enableValue: true,
        valueFormatter: (params: { value: number | string }) =>
          typeof params.value === 'number'
            ? params.value.toLocaleString('fr-BF')
            : String(params.value ?? ''),
      });
    }
    return defs;
  }

  private humanize(field: string): string {
    return field
      .split('_')
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(' ');
  }
}
